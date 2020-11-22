import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.MessageTemplate.MatchExpression;

import java.util.ArrayList;

public class ClientMain extends Agent {
	protected void setup() {
		String local_name = getLocalName();

		// Получение названий входного и выходного файла
		Object[] args = getArguments();

		// Чтение данных из файла
		String input_filename = args[0].toString();
		String output_filename = args[1].toString();
		IOUtility io_utility = new IOUtility(input_filename, output_filename);
		ArrayList<TouristItem> items = io_utility.readItems();

		// Подсчёт количества предметов
		int items_count = items.size();

		addBehaviour(new SimpleBehaviour(this) {
			// текущий шаг
			private int step = 1;
			// идентификатор сервера
			private AID server;
			// идентификатор переписки
			private String conversation_Id;
			// шаблон для приёма сообщений от сервера
			private MessageTemplate template;
			// выходные данные (имена туристов и их предметы)
			private TouristData[] tourist_data = new TouristData[0];

			// обработка исключения
			private void handleException(Exception e, String message) {
				System.out.print(local_name + ": " + message);
				if (e != null) {
					String exception_message = e.getMessage();
					if (!exception_message.equals("")) {
						System.out.print("(" + exception_message + ")");
					}
					e.printStackTrace();
				}
				System.out.println();
				doDelete();
				this.step = 0;
			}

			// приём сообщения (строка)
			private String receiveString(MessageTemplate template) {
				ACLMessage message = blockingReceive(template);
				return message.getContent();
			}

			// приём сообщения (целое число)
			private int receiveInteger(MessageTemplate template) {
				String content = receiveString(template);
				return Integer.parseInt(content);
			}

			@Override
			public void action() {
				switch (this.step) {
					case 1 -> {
						// Связь с сервером
						ACLMessage connection_message = new ACLMessage(ACLMessage.INFORM);
						connection_message.addReceiver(new AID("server", false));
						connection_message.setContent("connection");
						connection_message.setConversationId("connection");
						this.myAgent.send(connection_message);

						// Ожидание ответа от сервера
						ACLMessage reply = blockingReceive();
						String reply_message = reply.getContent();

						// Если не удалось подключиться, то завершение работы
						if (!reply_message.equals("connected")) {
							try {
								throw new Exception(reply_message);
							} catch (Exception e) {
								handleException(e, "Не удалось подключиться к серверу");
								break;
							}
						}

						// Иначе запоминаем идентификатор переписки и отправителя
						// и переходим к следующему шагу
						this.server = reply.getSender();
						this.conversation_Id = reply.getConversationId();
						this.step = 2;
					}
					case 2 -> {
						/*
						 *   Формат отправки начальных данных:
						 *    Количество предметов (n)
						 *        Название предмета 1/n
						 *        Название предмета 2/n
						 *        ...
						 *        Название предмета n/n
						 *        Вес предмета 1/n
						 *        Вес предмета 2/n
						 *        ...
						 *        Вес предмета n/n
						 * */

						// Описание сообщения
						ACLMessage item_message = new ACLMessage(ACLMessage.INFORM);
						item_message.addReceiver(this.server);
						item_message.setConversationId(this.conversation_Id);

						// Отправка количества предметов
						item_message.setContent(Integer.toString(items_count));
						this.myAgent.send(item_message);

						// Отправка названий предметов
						for (TouristItem item : items) {
							item_message.setContent(item.getName());
							this.myAgent.send(item_message);
						}

						// Отправка весов предметов
						for (TouristItem item : items) {
							String item_weight = Integer.toString(item.getWeight());
							item_message.setContent(item_weight);
							this.myAgent.send(item_message);
						}

						// Переход к следующему шагу
						this.step = 3;
					}
					case 3 -> {
						// Создаём шаблон для сообщения
						// (приём сообщений только от сервера)
						MatchExpression expression = (MatchExpression) message -> {
							int performative = message.getPerformative();
							AID sender = message.getSender();
							return  performative == ACLMessage.INFORM &&
									sender == server;
						};
						this.template = new MessageTemplate(expression);
					}
					case 4 -> {
						// Ожидание ответа от сервера (с уже распределенными весами)
						String content = receiveString(this.template);
						if (content.equals("results")) {
							// Если сервер сообщил о том, что
							// собирается отправлять результаты
							this.step = 5;
						} else {
							// Иначе просто выводим сообщение
							System.out.println(local_name + ": сервер сообщает - " + content);
						}
					}
					case 5 -> {
						/*
						 *   Формат приёма конечных данных:
						 *    Количество туристов (n)
						 *        Имя 1-го туриста 1/n
						 *        Количество предметов у 1-го туриста (m_1)
						 *            Название 1-го предмета 1/m_1
						 *            Вес 1-го предмета 1/m_1
						 *            Количество 1-го предмета 1/m_1
						 *            ...
						 *            Название m_1-го предмета m_1/m_1
						 *            Вес m_1-го предмета m_1/m_1
						 *            Количество 2-го предмета m_1/m_1
						 *        ...
						 *	  Имя n-го туриста n/n
						 *        Количество предметов у n-го туриста (m_n)
						 *            Название 1-го предмета 1/m_n
						 *            Вес 1-го предмета 1/m_n
						 *            Количество 1-го предмета 1/m_n
						 *            ...
						 *            Название m_n-го предмета m_n/m_n
						 *            Вес m_n-го предмета m_n/m_n
						 *            Количество 2-го предмета m_n/m_n
						 * */
						int tourist_count;

						// Получение количества туристов
						try {
							tourist_count = receiveInteger(this.template);
						} catch (NumberFormatException e) {
							handleException(e, "ошибка! получено некорректное значение числа туристов");
							break;
						}

						// создание выходного массива данных о туристах
						tourist_data = new TouristData[tourist_count];

						for (int i = 0; i < tourist_count; ++i) {
							// Получение имени туриста
							String tourist_name = receiveString(this.template);

							// Получение количества предметов
							int items_count;
							try {
								items_count = receiveInteger(this.template);
							} catch (NumberFormatException e) {
								handleException(e, "ошибка! получено некорректное значение числа предметов");
								break;
							}

							// Подготовка списка предметов
							ArrayList<TouristItem> items = new ArrayList<>();

							// Получение предметов
							for (int j = 0; j < items_count; ++j) {
								// Получение названия предмета
								String item_name = receiveString(this.template);
								int item_weight, item_count;

								// Получение веса предмета
								try {
									item_weight = receiveInteger(this.template);
								} catch (NumberFormatException e) {
									handleException(e, "ошибка! получено некорректное значение веса предмета");
									break;
								}

								// Получение количества предмета
								try {
									item_count = receiveInteger(this.template);
								} catch (NumberFormatException e) {
									handleException(e, "ошибка! получено некорректное значение количества предмета");
									break;
								}

								// Добавление предмета в список
								items.add(new TouristItem(item_name, item_weight, item_count));
							}

							// Добавление туриста в массив
							tourist_data[i] = new TouristData(tourist_name, items);
						}

						// Переход к следующему шагу
						this.step = 6;
					}
					case 6 -> {
						// Сохранение полученных данных в файл
						io_utility.writeItems(tourist_data);

						// Завершение работы
						doDelete();
						this.step = 0;
					}
				}
			}

			@Override
			public boolean done() {
				return this.step == 0;
			}
		});

	}
}


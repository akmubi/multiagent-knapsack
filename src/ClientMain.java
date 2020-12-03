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
		System.out.println(local_name + ": название входного файла - " + input_filename);
		String output_filename = args[1].toString();
		System.out.println(local_name + ": название выходного файла - " + output_filename);
		IOUtility io_utility = new IOUtility(input_filename, output_filename);
		System.out.println(local_name + ": чтение предметов...");
		ArrayList<TouristItem> items = io_utility.readItems();

		addBehaviour(new SimpleBehaviour(this) {
			// текущий шаг
			private int step = 1;
			// идентификатор сервера
			private AID server;
			// шаблон для приёма сообщений от сервера
			private MessageTemplate template;
			// выходные данные (имена туристов и их предметы)
			private final ArrayList<TouristData> tourist_data = new ArrayList<>();

			private int tourists_count = 0;

			// обработка исключения
			private void handleException(Exception e) {
				e.printStackTrace();
				doDelete();
				this.step = 0;
			}

			@Override
			public void action() {
				switch (this.step) {
					case 1 -> {
						// Связь с сервером
						System.out.println(local_name + ": подключение к серверу");
						ACLMessage connection_message = new ACLMessage(ACLMessage.INFORM);
						connection_message.addReceiver(new AID("server", false));
						connection_message.setConversationId("connection");
						this.myAgent.send(connection_message);

						// Ожидание ответа от сервера
						System.out.println(local_name + ": ожидание ответа от сервера");
						ACLMessage reply = blockingReceive();
						String reply_message = reply.getContent();

						// Если не удалось подключиться, то завершение работы
						if (!reply_message.equals("connected")) {
							try {
								throw new Exception(reply_message);
							} catch (Exception e) {
								handleException(e);
								break;
							}
						}

						System.out.println(local_name + ": подключены");

						// Иначе запоминаем идентификатор переписки и отправителя
						// и переходим к следующему шагу
						this.server = reply.getSender();
						this.step = 2;
					}
					case 2 -> {
						// Отправка предметов
						System.out.println(local_name + ": отправка предметов...");
						ACLMessage items_message = new ACLMessage(ACLMessage.INFORM);
						items_message.addReceiver(this.server);
						items_message.setContent(TouristItem.toString(items));
						this.myAgent.send(items_message);

						// Переход к следующему шагу
						this.step = 3;
					}
					case 3 -> {
						// Создаём шаблон для сообщения
						// (приём сообщений только от сервера)
						MatchExpression expression = (MatchExpression) message -> {
							int performative = message.getPerformative();
							String sender_name = message.getSender().getName();
							return  performative == ACLMessage.INFORM &&
									sender_name.equals(server.getName());
						};
						this.template = new MessageTemplate(expression);
						this.step = 4;
					}
					case 4 -> {
						// Ожидание ответа от сервера (с уже распределенными весами)
						System.out.println(local_name + ": ожидание получения результатов...");
						ACLMessage inform = this.myAgent.blockingReceive(this.template);
						String conversation_Id = inform.getConversationId();
						String content = inform.getContent();
						if (conversation_Id.equals("results")) {
							// Если сервер сообщил о том, что
							// собирается отправлять результаты
							try {
								// Получаем количество туристов
								this.tourists_count = Integer.parseInt(content);
							} catch (NumberFormatException e) {
								handleException(e);
								break;
							}
							this.step = 5;
						} else {
							// Иначе просто выводим сообщение
							System.out.println(local_name + ": сервер сообщает - " + content);
						}
					}
					case 5 -> {
						System.out.println(local_name + ": получение результатов...");
						ACLMessage results = this.myAgent.blockingReceive(this.template);
						try {
							// Добавляем полученные данные в список
							this.tourist_data.add(TouristData.parseTouristData(results.getContent()));
						} catch (Exception e) {
							handleException(e);
							break;
						}

						// Как только получим результаты всех туристов
						if (this.tourist_data.size() == this.tourists_count) {
							// Переходим к следующему шагу
							this.step = 6;
						}
					}
					case 6 -> {
						// Сохранение полученных данных в файл
						System.out.println(local_name + ": сохранение данных в файл");
						io_utility.writeItems(this.tourist_data);

						// Завершение работы
						System.out.println(local_name + ": завершение работы");

						this.myAgent.doDelete();

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

	protected void takeDown() {
		System.exit(0);
	}
}
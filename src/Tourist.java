import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.MessageTemplate.MatchExpression;

import java.util.ArrayList;
import java.util.Arrays;

public class Tourist extends Agent {
	private static final String STATE_A = "A";
	private static final String STATE_B = "B";
	private static final String STATE_C = "C";
	private static final String STATE_D = "D";
	private static final String STATE_E = "E";

	protected void setup() {
		String local_name = getLocalName();

		// получаем аргументы
		Object[] args = getArguments();
		// среднее среди всех предметов
		double average = (double)args[0];
		// веса предметов
		ArrayList<TouristItem> items = new ArrayList<>(Arrays.asList((TouristItem[]) args[1]));

		int sum = TouristItem.getSum(items);

		FSMBehaviour fsm = new FSMBehaviour();

		// добавляем состояния
		fsm.registerFirstState(new OneShotBehaviour(this) {
			private int tourist_type = -1;

			@Override
			public void action() {
				if (sum > average) {
					System.out.println(local_name + ": регистрация как overflow");
					DFUtilities.register(this.myAgent, "overflow");
					this.tourist_type = 1;
				} else {
					System.out.println(local_name + ": регистрация как lack");
					DFUtilities.register(this.myAgent, "lack");
					this.tourist_type = 0;
				}
			}

			public int onEnd() {
				return tourist_type;
			}
		}, STATE_A);

		// LackBehaviour
		SequentialBehaviour lack_sequential = new SequentialBehaviour();

		// отправка запросов overflow туристам
		lack_sequential.addSubBehaviour(new SimpleBehaviour(this) {
			private int step = 1;
			private AID[] overflow_tourists;
			private MessageTemplate template;
			private ArrayList<ACLMessage> replies;
			private int current_max_weight;

			@Override
			public void action() {
				switch (this.step) {
					case 1 -> {
						System.out.println(local_name + ": текущие предметы - " + TouristItem.toString(items));

						// вычисляем разницу
						System.out.print(local_name + ": вычисление разницы");
						int difference = (int) average - TouristItem.getSum(items);
						System.out.println(" " + difference);

						// если турист приблизился к среднему значению,
						// то переход к следующему поведенеию
						if (difference < 1) {
							System.out.println(local_name + ": переход к следующему поведению");
							this.step = 0;
							break;
						}

						// поиск overflow туристов
						System.out.println(local_name + ": поиск overflow туристов...");
						this.overflow_tourists = DFUtilities.searchService(this.myAgent, "overflow");
						// если overflow туристов нет, то
						// переход к следующему поведению
						if (overflow_tourists.length < 1) {
							System.out.println(local_name + ": overflow туристов нет. Переход к следующему поведению");
							this.step = 0;
						} else {
							// иначе отправляем запрос
							System.out.println(local_name + ": отправка запроса с разницей...");
							ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
							cfp.setContent(Integer.toString(difference));
							for (AID tourist : overflow_tourists) {
								cfp.addReceiver(tourist);
							}

							// отправка запроса
							this.myAgent.send(cfp);

							// подготовка списка к ответам
							this.replies = new ArrayList<>();
							this.current_max_weight = -1;

							// переход к следующему шагу
							this.step = 2;
						}
					}
					case 2 -> {
						// Подготавливаем шаблон для получения сообщения
						MatchExpression expression = (MatchExpression) aclMessage -> {
							int performative = aclMessage.getPerformative();
							return (performative == ACLMessage.PROPOSE || performative == ACLMessage.REFUSE);
						};
						this.template = new MessageTemplate(expression);

						// переход к следующему шагу
						this.step = 3;
						System.out.println(local_name + ": ожидание ответа от всех адресатов...");
					}
					case 3 -> {
						ACLMessage reply = this.myAgent.receive(this.template);
						if (reply != null) {
							// добавляем полученный ответ в список
							System.out.println(local_name + ": получен ответ. Добавление в список (" + this.replies.size() + "/" + this.overflow_tourists.length + ")");
							this.replies.add(reply);
						} else {
							block();
						}

						if (this.replies.size() == this.overflow_tourists.length) {
							// если получены все ответы, то
							// переход к следующему шагу
							System.out.println(local_name + ": получены все сообщения (" + this.overflow_tourists.length + ")");
							this.step = 4;
						}
					}
					case 4 -> {
						// выбираем самое наибольшее значение полученного веса
						// и запоминаем отправителя
						System.out.println(local_name + ": выбор наибольшего значения полученного веса...");
						AID best_sender = null;
						for (ACLMessage reply : this.replies) {
							int performative = reply.getPerformative();
							if (performative == ACLMessage.PROPOSE) {
								try {
									int weight = Integer.parseInt(reply.getContent());
									if (weight > this.current_max_weight) {
										best_sender = reply.getSender();
										this.current_max_weight = weight;
									}
								} catch (NumberFormatException e) {
									e.printStackTrace();
									this.myAgent.doDelete();
									this.step = 0;
								}
							}
						}

						// если все отказались, то
						// это означает, что у них больше нет предметов
						// с меньшим весом, поэтому
						// завершаем работу
						if (this.current_max_weight == -1) {
							System.out.println(local_name + ": все отказались");
							this.step = 0;
							break;
						}

						System.out.println(local_name + ": максимальный вес - " + this.current_max_weight);

						// отправка ответа
						System.out.println(local_name + ": отправка ответа...");
						ACLMessage proposal_reply;
						for (AID tourist : this.overflow_tourists) {
							if (best_sender == tourist) {
								proposal_reply = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
							} else {
								proposal_reply = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
							}
							this.myAgent.send(proposal_reply);
						}

						// подготаливаем шаблон для получения
						final AID safe_best_sender = best_sender;
						MatchExpression expression = (MatchExpression) aclMessage -> {
							int performative = aclMessage.getPerformative();
							String sender = aclMessage.getSender().getName();
							return (performative == ACLMessage.INFORM) &&
									sender.equals(safe_best_sender.getName());
						};
						this.template = new MessageTemplate(expression);

						// переход к следующему шагу
						this.step = 5;
					}
					case 5 -> {

						ACLMessage inform = this.myAgent.receive(this.template);
						if (inform != null) {
							// получено название предмета
							System.out.println(local_name + ": получено название предмета");
							items.add(new TouristItem(inform.getContent(), this.current_max_weight));

							// переходим к самому первому шагу
							this.step = 1;
						} else {
							block();
						}
					}
				}
			}

			@Override
			public boolean done() {
				return this.step == 0;
			}
		});

		ParallelBehaviour lack_parallel = new ParallelBehaviour(ParallelBehaviour.WHEN_ANY);

		// мониторинг агентов и выход с отправкой, когда агентов в DF не останется
		lack_parallel.addSubBehaviour(new SimpleBehaviour(this) {
			private boolean finished = false;

			@Override
			public void action() {
				AID[] overflow_tourists = DFUtilities.searchService(this.myAgent, "overflow");
				if (overflow_tourists.length < 1) {
					// отправляем все веса
					System.out.println(local_name + ": overflow туристов больше нет");
					System.out.println(local_name + ": отправка весов и завершение работы");
					sendResults(items);
					// и заканчиваем
					this.finished = true;
				} else {
					block(300);
				}
			}

			@Override
			public boolean done() {
				return this.finished;
			}
		});

		// основное поведение (обмен предметами с другими туристами)
		lack_parallel.addSubBehaviour(new SimpleBehaviour(this) {
			private int step = 1;
			private MessageTemplate template;
			private final DataStore global_data = getDataStore();

			private static final int CURRENT_INDEX_KEY = 1;
			private static final int REQUESTED_WEIGHT_KEY = 2;
			private static final int CURRENT_SENDER_KEY = 3;
			private static final int SENDER_ITEM_WEIGHT_KEY = 4;

			@Override
			public void action() {
				switch (this.step) {
					case 1 -> {
						// очищаем глобальные данные
						this.global_data.clear();

						System.out.println(local_name + ": текущие предметы - " + TouristItem.toString(items));

						// Подготавливаем шаблон для получения сообщения
						MatchExpression expression = (MatchExpression) message -> {
							int performative = message.getPerformative();
							return performative == ACLMessage.CFP;
						};
						this.template = new MessageTemplate(expression);
						this.step = 2;
					}
					case 2 -> {
						// получение запроса на обмен
						ACLMessage request = this.myAgent.receive(this.template);
						if (request != null) {
							System.out.println(local_name + ": запрос на обмен получен");
							try {
								int requested_weight = Integer.parseInt(request.getContent());
								this.global_data.put(REQUESTED_WEIGHT_KEY, requested_weight);
							} catch (Exception e) {
								e.printStackTrace();
								this.myAgent.doDelete();
								this.step = 0;
							}

							// сохраняем отправителя
							this.global_data.put(CURRENT_SENDER_KEY, request.getSender());
							this.global_data.put(CURRENT_INDEX_KEY, -1);
							this.step = 3;
						} else {
							block();
						}
					}
					case 3 -> {
						// подготавливаем сообщение для отправки
						ACLMessage propose = new ACLMessage(ACLMessage.PROPOSE);
						propose.addReceiver((AID)this.global_data.get(CURRENT_SENDER_KEY));

						int requested_weight = (int)this.global_data.get(REQUESTED_WEIGHT_KEY);

						// находим запрашиваемый вес
						int index = TouristItem.searchByWeight(items, requested_weight);
						if (index == -1) {
							index = TouristItem.searchByWeight(items, requested_weight - 1);
							// если вес не был найден, то
							// переходим на следующий шаг, где
							// будет отправлено сообщение об отказе
							if (index == -1) {
								this.step = 4;
								break;
							}
						}
						// иначе отправляем найденный вес
						String content = Integer.toString(items.get(index).getWeight());
						propose.setContent(content);
						System.out.println(local_name + ": отправка PROPOSE сообщения");
						this.myAgent.send(propose);

						// переходим на следующий шаг
						this.step = 5;
					}
					case 4 -> {
						// подготавливаем сообщения для отказа
						ACLMessage refuse = new ACLMessage(ACLMessage.REFUSE);
						refuse.addReceiver((AID)this.global_data.get(CURRENT_SENDER_KEY));
						// отправялем
						System.out.println(local_name + ": отправка REFUSE сообщения");
						this.myAgent.send(refuse);
						// переходим в начало
						this.step = 1;
					}
					case 5 -> {
						// Подготавливаем шаблон для получения сообщения
						MatchExpression expression = (MatchExpression) message -> {
							int performative = message.getPerformative();
							String sender = message.getSender().getName();
							return (performative == ACLMessage.ACCEPT_PROPOSAL || performative == ACLMessage.REJECT_PROPOSAL) &&
									sender.equals(((AID)this.global_data.get(CURRENT_SENDER_KEY)).getName());
						};
						this.template = new MessageTemplate(expression);
						this.step = 6;
					}
					case 6 -> {
						ACLMessage reply = this.myAgent.receive(this.template);
						if (reply != null) {
							int performative = reply.getPerformative();
							if (performative == ACLMessage.ACCEPT_PROPOSAL) {
								System.out.println(local_name + ": получено ACCEPT_PROPOSAL");
								try {
									int sender_weight = Integer.parseInt(reply.getContent());
									this.global_data.put(SENDER_ITEM_WEIGHT_KEY, sender_weight);
								} catch (NumberFormatException e) {
									e.printStackTrace();
									this.myAgent.doDelete();
									this.step = 0;
									break;
								}
								this.step = 7;
							} else {
								System.out.println(local_name + ": получено REJECT_PROPOSAL");
								this.step = 1;
							}
						} else {
							block();
						}
					}
					case 7 -> {
						// Отправляем сообщение с названием предмета
						System.out.println(local_name + ": отправка названия предмета");
						ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
						AID current_sender = (AID)this.global_data.get(CURRENT_SENDER_KEY);
						inform.addReceiver(current_sender);
						int current_index = (int)this.global_data.get(CURRENT_INDEX_KEY);
						inform.setContent(items.get(current_index).getName());

						this.myAgent.send(inform);

						// подготовка шаблона
						MatchExpression expression = (MatchExpression) message -> {
							int performative = message.getPerformative();
							String sender = message.getSender().getName();
							return performative == ACLMessage.INFORM &&
									sender.equals(current_sender.getName());
						};
						this.template = new MessageTemplate(expression);

						// удаление предмета
						System.out.println(local_name + ": удаление предмета");
						items.remove(current_index);
						this.step = 8;
					}
					case 8 -> {
						// теперь получаем название от другого туриста
						ACLMessage another_inform = this.myAgent.receive(this.template);
						if (another_inform != null) {
							// добавляем предмет в список
							String another_item_name = another_inform.getContent();
							System.out.println(local_name + ": получено название от другого предмета (" + another_item_name + ")");
							int another_item_weight = (int)this.global_data.get(SENDER_ITEM_WEIGHT_KEY);
							System.out.println(local_name + ": добавление предмета в список (" + another_item_weight + ")");
							items.add(new TouristItem(another_item_name, another_item_weight));
							this.step = 1;
						} else {
							block();
						}
					}
				}
			}

			@Override
			public boolean done() {
				return this.step == 0;
			}
		});
		lack_sequential.addSubBehaviour(lack_parallel);

		fsm.registerState(lack_sequential, STATE_B);

//		SequentialBehaviour overflow_sequential = new SequentialBehaviour();

		ParallelBehaviour overflow_parallel = new ParallelBehaviour(ParallelBehaviour.WHEN_ANY);

		// мониторинг агентов и выход, когда агентов в DF не останется
		overflow_parallel.addSubBehaviour(new SimpleBehaviour(this) {
			private boolean finished = false;
			@Override
			public void action() {
				AID[] lack_tourists = DFUtilities.searchService(this.myAgent, "lack");
				if (lack_tourists.length < 1) {
					System.out.println(local_name + ": lack туристов нет. Переход к следующему поведению");
					finished = true;
				} else {
					block(300);
				}
			}

			@Override
			public boolean done() {
				return finished;
			}

			@Override
			public int onEnd() {
				return 0;
			}
		});

		// ожидание и обработка запросов от lack туристов
		overflow_parallel.addSubBehaviour(new SimpleBehaviour(this) {
			private int step = 1;
			private MessageTemplate template;
			private int next_state = 0;
			private final DataStore global_data = getDataStore();

			private static final int REQUESTED_WEIGHT_INDEX_KEY = 1;
			private static final int SENDER_KEY = 2;
			@Override
			public void action() {
				switch (this.step) {
					case 1 -> {
						this.global_data.clear();
						System.out.println(local_name + ": текущие предметы - " + TouristItem.toString(items));

						MatchExpression expression = (MatchExpression) message -> {
								int performative = message.getPerformative();
								return performative == ACLMessage.CFP;
						};
						this.template = new MessageTemplate(expression);
						this.step = 2;

						// проверка суммы весов
						int sum = TouristItem.getSum(items);
						if (sum <= average) {
							// костыль
							this.next_state = 1;
							this.step = 0;
						}
					}
					case 2 -> {
						System.out.println(local_name + ": ожидание запроса...");
						ACLMessage cfp_message = this.myAgent.receive(this.template);
						if (cfp_message != null) {
							System.out.println(local_name + ": получен запрос");
							try {
								// чтение полученного веса
								int weight = Integer.parseInt(cfp_message.getContent());
								System.out.println(local_name + ": запрашиваемый вес - " + weight);
								// нахождение наибольшего веса, не превышающего полученный вес
								int index = TouristItem.searchMaxNotExceed(items, weight);
								this.global_data.put(REQUESTED_WEIGHT_INDEX_KEY, index);
							} catch (NumberFormatException e) {
								e.printStackTrace();
								this.myAgent.doDelete();
								this.step = 0;
								break;
							}

							this.global_data.put(SENDER_KEY, cfp_message.getSender());
							this.step = 3;
						} else {
							block();
						}
					}
					case 3 -> {
						// выбор сообщения для отправки
						ACLMessage reply;
						int index = (int)this.global_data.get(REQUESTED_WEIGHT_INDEX_KEY);
						if (index != -1) {
							System.out.println(local_name + ": запрашиваемый вес был найден");
							reply = new ACLMessage(ACLMessage.PROPOSE);
							int found_weight = items.get(index).getWeight();
							reply.setContent(Integer.toString(found_weight));
							this.step = 4;
						} else {
							System.out.println(local_name + ": запрашиваемый вес не найден");
							reply = new ACLMessage(ACLMessage.REFUSE);
							this.step = 1;
						}
						reply.setConversationId("");
						AID sender = (AID)this.global_data.get(SENDER_KEY);
						reply.addReceiver(sender);
						System.out.println(local_name + ": отправка ответа");
						this.myAgent.send(reply);

						MatchExpression expression = (MatchExpression) message -> {
							int performative = message.getPerformative();
							String current_sender = message.getSender().getName();
							return (performative == ACLMessage.ACCEPT_PROPOSAL || performative == ACLMessage.REJECT_PROPOSAL) &&
									current_sender.equals(sender.getName()) ;
						};
						this.template = new MessageTemplate(expression);
					}
					case 4 -> {
						System.out.println(local_name + ": ожидание ответа на ответ");
						ACLMessage proposal_reply = this.myAgent.receive(this.template);
						if (proposal_reply != null) {
							int performative = proposal_reply.getPerformative();
							if (performative == ACLMessage.ACCEPT_PROPOSAL) {
								System.out.println(local_name + ": получено ACCEPT_PROPOSAL");
								// если сообщение было принято, то
								// отправляем название предмета и
								// удаляем предмет из списка
								int index = (int)this.global_data.get(REQUESTED_WEIGHT_INDEX_KEY);
								String item_name = items.get(index).getName();
								ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
								inform.addReceiver(proposal_reply.getSender());
								inform.setContent(item_name);
								System.out.println(local_name + ": отправка названия предмета");
								this.myAgent.send(inform);

								items.remove(index);
							} else {
								System.out.println(local_name + ": получено REJECT_PROPOSAL");
							}
							this.step = 1;
						} else {
							block();
						}
					}
				}
			}

			@Override
			public boolean done() {
				return this.step == 0;
			}

			@Override
			public int onEnd() {
				return this.next_state;
			}
		});

		fsm.registerState(overflow_parallel, STATE_C);

		// основное поведение (обмен предметами с lack туристами)
		fsm.registerState(new SimpleBehaviour(this) {
			private int step = 1;
			private MessageTemplate template;
			private final DataStore global_data = getDataStore();
			private int current_weight_index = 0;
			private int current_max_weight = -1;
			private ArrayList<ACLMessage> replies;

			private static final int TOURIST_COUNT_KEY = 1;
			private static final int CURRENT_BEST_SENDER = 2;

			@Override
			public void action() {
				switch (this.step) {
					case 1 -> {
						this.current_max_weight = -1;
						this.global_data.clear();
						System.out.println(local_name + ": текущие предметы - " + TouristItem.toString(items));

						AID[] normal_tourists = DFUtilities.searchService(this.myAgent, "normal");
						if (normal_tourists.length < 1) {
							this.step = 0;
							break;
						}

						// вычисляем разность между суммой весов предметов и
						// средним значением без учёта веса текущего предмета
						int sum = TouristItem.getSum(items);
						int current_weight = items.get(this.current_weight_index).getWeight();
						System.out.println(local_name + ": вычисление разности без текущего веса");
						int difference = sum - (int)average - current_weight;
						System.out.println(local_name + ": разница - " + difference);
						// заканчиваем, только тогда, когда вес предметов близок к среднему или
						// когда все предметы были просмотрены
						if (Math.abs(difference) < 2 || current_weight_index == items.size() - 1) {
							System.out.println(local_name + ": завершение поведения");
							this.step = 0;
							break;
						}

						ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
						cfp.setContent(Integer.toString(difference));

						for (AID tourist : normal_tourists) {
							cfp.addReceiver(tourist);
						}
						System.out.println(local_name + ": отправка сообщений на обмен...");
						this.myAgent.send(cfp);

						// подготовка к получению сообщений
						this.replies = new ArrayList<>();
						this.global_data.put(TOURIST_COUNT_KEY, normal_tourists.length);

						MatchExpression expression = (MatchExpression) message -> {
							int performative = message.getPerformative();
							return (performative == ACLMessage.PROPOSE || performative == ACLMessage.REFUSE);
						};
						this.template = new MessageTemplate(expression);
						this.step = 2;
					}
					case 2 -> {
						ACLMessage reply = this.myAgent.receive(this.template);
						if (reply != null) {
							replies.add(reply);
						} else {
							block();
						}

						int tourist_count = (int)this.global_data.get(TOURIST_COUNT_KEY);
						if (tourist_count == this.replies.size()) {
							System.out.println(local_name + ": получены  все сообщения");
							this.step = 3;
						}
					}
					case 3 -> {
						System.out.println(local_name + ": нахождение набольшего значения полученного веса...");
						AID best_sender = null;
						int max_weight = -1;
						for (ACLMessage reply : this.replies) {
							int performative = reply.getPerformative();
							if (performative == ACLMessage.PROPOSE) {
								try {
									int weight = Integer.parseInt(reply.getContent());
									if (weight > max_weight) {
										max_weight = weight;
										best_sender = reply.getSender();
									}
								} catch (NumberFormatException e) {
									e.printStackTrace();
									this.myAgent.doDelete();
									this.step = 0;
									break;
								}
							}
						}

						// если никто не смог отдать предмет, то
						// пробуем другой предмет
						if (max_weight == -1) {
							System.out.println(local_name + ": все отказали. Переход к следующему весу");
							this.current_weight_index++;
							this.step = 1;
							break;
						}

						System.out.println(local_name + ": наилучший вес - " + max_weight);
						this.current_max_weight = max_weight;
						this.global_data.put(CURRENT_BEST_SENDER, best_sender);

						// отказываем всем кроме того, кто предоставил
						// лучший вес

						ACLMessage reject_message = new ACLMessage(ACLMessage.REJECT_PROPOSAL);

						for (ACLMessage reply : this.replies) {
							int performative = reply.getPerformative();
							AID sender = reply.getSender();
							if (performative == ACLMessage.PROPOSE && sender != best_sender) {
								reject_message.addReceiver(sender);
							}
						}
						System.out.println(local_name + ": отправка ответов (REJECT_PROPOSAL)");
						this.myAgent.send(reject_message);

						// отправляем тому, кто предоставил лучший вес, текущий вес
						int current_item_weight = items.get(this.current_weight_index).getWeight();
						ACLMessage accept_message = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
						accept_message.setContent(Integer.toString(current_item_weight));
						accept_message.addReceiver(best_sender);
						System.out.println(local_name + ": отправка ответа (ACCEPT_PROPOSAL)");
						this.myAgent.send(accept_message);

						AID safe_best_sender = best_sender;
						MatchExpression expression = (MatchExpression) message -> {
							int performative = message.getPerformative();
							String sender = message.getSender().getName();
							return performative == ACLMessage.INFORM &&
									sender.equals(safe_best_sender.getName());
						};
						this.template = new MessageTemplate(expression);

						this.step = 4;
					}
					case 4 -> {
						System.out.println(local_name + ": ожидание сообщения с названием предмета...");
						ACLMessage inform_message = this.myAgent.receive(this.template);
						if (inform_message != null) {
							System.out.println(local_name + ": название предмета получено");
							String item_name = inform_message.getContent();
							int item_weight = this.current_max_weight;
							System.out.println(local_name + ": добавление предмета");
							items.add(new TouristItem(item_name, item_weight));
							this.step = 5;
						} else {
							block();
						}
					}
					case 5 -> {
						// отправка названия своего предмета
						ACLMessage another_inform_message = new ACLMessage(ACLMessage.INFORM);
						another_inform_message.addReceiver((AID)this.global_data.get(CURRENT_BEST_SENDER));
						String current_item_name = items.get(this.current_weight_index).getName();
						another_inform_message.setContent(current_item_name);
						System.out.println(local_name + ": отправка названия обмениваемого предмета...");
						this.myAgent.send(another_inform_message);

						// удаление этого предмета из списка
						System.out.println(local_name + ": удаление предмета");
						items.remove(current_weight_index);
//						this.current_weight_index++;
						this.step = 1;
					}
				}
			}

			@Override
			public boolean done() {
				if (this.step == 0) {
					System.out.println(local_name + ": отправка предметов и завершение работы");
					sendResults(items);
					return true;
				}
				return false;
			}
		}, STATE_D);

		fsm.registerLastState(new OneShotBehaviour(this) {
			@Override
			public void action() {
				// Удаление записи в DF
				DFUtilities.deregister(this.myAgent);
				DFUtilities.register(this.myAgent, "normal");

				// и завершение работы агента
				this.myAgent.doDelete();
			}
		}, STATE_E);

		// добавляем переходы

		fsm.registerTransition(STATE_A, STATE_B, 0);
		fsm.registerTransition(STATE_A, STATE_C, 1);
		fsm.registerTransition(STATE_C, STATE_B, 0);
		fsm.registerTransition(STATE_C, STATE_D, 1);
		fsm.registerDefaultTransition(STATE_D, STATE_E);
		fsm.registerDefaultTransition(STATE_B, STATE_E);

		addBehaviour(fsm);
	}

	private void sendResults(ArrayList<TouristItem> items) {
		TouristData data = new TouristData(getLocalName(), items);
		ACLMessage results_message = new ACLMessage(ACLMessage.INFORM);
		results_message.setConversationId("results");
		results_message.setContent(TouristData.toString(data));
		results_message.addReceiver(new AID("server", AID.ISLOCALNAME));
		send(results_message);
	}
}

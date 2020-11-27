import jade.core.AID;
import jade.core.Agent;
import jade.core.AgentContainer;
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
						int difference = (int) average - TouristItem.getSum(items);
						System.out.println(local_name + ": текущая разница - " + difference);

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
						System.out.println(local_name + ": выбор наибольшего значения полученных весов...");
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

						System.out.println(local_name + ": лучший вес - " + this.current_max_weight);

						// отправка ответа
						System.out.println(local_name + ": отправка ответа...");
						ACLMessage proposal_reply;
						for (AID tourist : this.overflow_tourists) {
							if (best_sender.getName().equals(tourist.getName())) {
								proposal_reply = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
							} else {
								proposal_reply = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
							}
							proposal_reply.addReceiver(tourist);
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
		lack_sequential.addSubBehaviour(new OneShotBehaviour(this) {
			@Override
			public void action() {
				System.out.println(local_name + ": удаление записи о регистрации");
				DFUtilities.deregister(this.myAgent);
				System.out.println(local_name + ": регистрация как normal");
				DFUtilities.register(this.myAgent, "normal");
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
			private final DataStore global_data = getDataStore();

			@Override
			public void action() {
				switch(this.step) {
					case 1 -> {
						System.out.println(local_name + ": текущие предметы - " + TouristItem.toString(items));
						this.step = 2;
					}
					case 2 -> {
						ACLMessage message = this.myAgent.receive();
						if (message != null) {
							int performative = message.getPerformative();
							AID sender = message.getSender();
							if (performative == ACLMessage.CFP) {
								System.out.println(local_name + ": получен запрос на обмен (" + message.getContent() + ")");
								int requested_weight;
								try {
									requested_weight = Integer.parseInt(message.getContent());
								} catch (NumberFormatException e ) {
									e.printStackTrace();
									this.myAgent.doDelete();
									this.step = 0;
									break;
								}

								ACLMessage reply;

								int index = TouristItem.searchByWeight(items, requested_weight);
								if (index == -1) {
									index = TouristItem.searchByWeight(items, requested_weight - 1);
								}

								if (index == -1) {
									System.out.println(local_name + ": подходящий вес не найден. Отклонение");
									reply = new ACLMessage(ACLMessage.REFUSE);
								} else {
									System.out.println(local_name + ": подходящий вес был найден (" + items.get(index).getWeight() + "). Отправка");
									reply = new ACLMessage(ACLMessage.PROPOSE);
									reply.setContent(Integer.toString(items.get(index).getWeight()));
									this.global_data.put(sender.getName(), items.get(index));
									items.remove(index);
								}
								reply.addReceiver(sender);
								this.myAgent.send(reply);
							} else if (performative == ACLMessage.ACCEPT_PROPOSAL) {
								System.out.println(local_name + ": получено одобрение");
								TouristItem item = (TouristItem)this.global_data.get(sender.getName());
								String item_name = item.getName();
								System.out.println(local_name + ": отправка имени предмета - " + item_name);
								ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
								inform.setContent(item_name);
								inform.addReceiver(sender);
								this.myAgent.send(inform);

								try  {
									int weight = Integer.parseInt(message.getContent());
									System.out.println(local_name + ": вес обмениваемого предмета - " + weight);
									this.global_data.put(sender.getName(), weight);
								} catch (NumberFormatException e) {
									e.printStackTrace();
									this.myAgent.doDelete();
									this.step = 0;
								}
							} else if (performative == ACLMessage.INFORM) {
								String new_item_name = message.getContent();
								System.out.println(local_name + ": получено название обмениваемого предмета - " + new_item_name);
								int weight = (int)this.global_data.get(sender.getName());
								TouristItem new_item = new TouristItem(new_item_name, weight);
								items.add(new_item);
								this.global_data.remove(sender.getName());
								this.step = 1;
							} else if (performative == ACLMessage.REJECT_PROPOSAL) {
								System.out.println(local_name + ": получено отклонение");
							}
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

		ParallelBehaviour overflow_parallel = new ParallelBehaviour(ParallelBehaviour.WHEN_ANY) {
			private final ArrayList<Behaviour> behaviours = new ArrayList<>();

			@Override
			public void addSubBehaviour(Behaviour behaviour) {
				super.addSubBehaviour(behaviour);
				this.behaviours.add(behaviour);
			}

			@Override
			public int onEnd() {
				int next_state = 1;
				for (Behaviour behaviour : this.behaviours) {
					next_state &= behaviour.onEnd();
				}
				return next_state;
			}
		};

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
				return 1;
			}
		});

		// ожидание и обработка запросов от lack туристов
		overflow_parallel.addSubBehaviour(new SimpleBehaviour(this) {
			private int step = 1;
			private int next_state = 1;
			private final DataStore global_data = getDataStore();

			@Override
			public void action() {
				switch(this.step) {
					case 1 -> {
						System.out.println(local_name + ": текущие предметы - " + TouristItem.toString(items));
						// проверка суммы весов
						int sum = TouristItem.getSum(items);
						if (sum <= average) {
							// костыль
							this.next_state = 0;
							this.step = 0;
							break;
						}
						this.step = 2;
					}
					case 2 -> {
						ACLMessage message = this.myAgent.receive();
						if (message != null) {
							int performative = message.getPerformative();
							AID sender = message.getSender();
							if (performative == ACLMessage.CFP) {
								System.out.println(local_name + ": получен запрос (" + message.getContent() + ")");
								int index;
								int weight;
								try {
									weight = Integer.parseInt(message.getContent());
									index = TouristItem.searchMaxNotExceed(items, weight);
									if (index != -1)
										System.out.println(local_name + ": INDEX = " + index +", WEIGHT = " + items.get(index));
									else
										System.out.println(local_name + ": INDEX не найден");
								} catch (NullPointerException e) {
									e.printStackTrace();
									this.step = 0;
									break;
								}
								ACLMessage reply;

								if (index != -1) {
									System.out.println(local_name + ": найден подходящий вес (" + weight + "). Отправка");
									reply = new ACLMessage(ACLMessage.PROPOSE);
									reply.setContent(Integer.toString(weight));

									this.global_data.put(sender.getName(), items.get(index));
									items.remove(index);
								} else {
									System.out.println(local_name + ": подходящий вес не найден. Отклонение");
									reply = new ACLMessage(ACLMessage.REFUSE);
								}
								reply.addReceiver(sender);
								this.myAgent.send(reply);
							} else if (performative == ACLMessage.ACCEPT_PROPOSAL) {
								System.out.println(local_name + ": получено одобрение");
								TouristItem item = (TouristItem)this.global_data.get(sender.getName());
								String item_name = item.getName();
								System.out.println(local_name + ": отправка имени предмета - " + item_name);
								ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
								inform.addReceiver(sender);
								inform.setContent(item_name);
								this.myAgent.send(inform);
								this.global_data.remove(sender.getName());

								this.step = 1;
							} else if (performative == ACLMessage.REJECT_PROPOSAL) {
								System.out.println(local_name + ": получено отклонение");
							}
						} else {
							block();
						}
					}
				}

				// switch (this.step) {
				// 	case 1 -> {
				// 		this.global_data.clear();
				// 		System.out.println(local_name + ": текущие предметы - " + TouristItem.toString(items));

				// 		MatchExpression expression = (MatchExpression) message -> {
				// 				int performative = message.getPerformative();
				// 				return performative == ACLMessage.CFP;
				// 		};
				// 		this.template = new MessageTemplate(expression);
				// 		this.step = 2;

				// 		// проверка суммы весов
				// 		int sum = TouristItem.getSum(items);
				// 		if (sum <= average) {
				// 			// костыль
				// 			this.next_state = 1;
				// 			this.step = 0;
				// 		}
				// 	}
				// 	case 2 -> {
				// 		System.out.println(local_name + ": ожидание запроса...");
				// 		ACLMessage cfp_message = this.myAgent.receive(this.template);
				// 		if (cfp_message != null) {
				// 			System.out.println(local_name + ": получен запрос");
				// 			try {
				// 				// чтение полученного веса
				// 				int weight = Integer.parseInt(cfp_message.getContent());
				// 				System.out.println(local_name + ": запрашиваемый вес - " + weight);
				// 				// нахождение наибольшего веса, не превышающего полученный вес
				// 				int index = TouristItem.searchMaxNotExceed(items, weight);
				// 				this.global_data.put(REQUESTED_WEIGHT_INDEX_KEY, index);
				// 			} catch (NumberFormatException e) {
				// 				e.printStackTrace();
				// 				this.myAgent.doDelete();
				// 				this.step = 0;
				// 				break;
				// 			}

				// 			this.global_data.put(SENDER_KEY, cfp_message.getSender());
				// 			this.step = 3;
				// 		} else {
				// 			block();
				// 		}
				// 	}
				// 	case 3 -> {
				// 		// выбор сообщения для отправки
				// 		ACLMessage reply;
				// 		int index = (int)this.global_data.get(REQUESTED_WEIGHT_INDEX_KEY);
				// 		if (index != -1) {
				// 			System.out.println(local_name + ": запрашиваемый вес был найден");
				// 			reply = new ACLMessage(ACLMessage.PROPOSE);
				// 			int found_weight = items.get(index).getWeight();
				// 			reply.setContent(Integer.toString(found_weight));
				// 			this.step = 4;
				// 		} else {
				// 			System.out.println(local_name + ": запрашиваемый вес не найден");
				// 			reply = new ACLMessage(ACLMessage.REFUSE);
				// 			this.step = 1;
				// 		}
				// 		reply.setConversationId("");
				// 		AID sender = (AID)this.global_data.get(SENDER_KEY);
				// 		reply.addReceiver(sender);
				// 		System.out.println(local_name + ": отправка ответа");
				// 		this.myAgent.send(reply);

				// 		MatchExpression expression = (MatchExpression) message -> {
				// 			int performative = message.getPerformative();
				// 			String current_sender = message.getSender().getName();
				// 			return (performative == ACLMessage.ACCEPT_PROPOSAL || performative == ACLMessage.REJECT_PROPOSAL) &&
				// 					current_sender.equals(sender.getName()) ;
				// 		};
				// 		this.template = new MessageTemplate(expression);
				// 	}
				// 	case 4 -> {
				// 		System.out.println(local_name + ": ожидание ответа на ответ");
				// 		ACLMessage proposal_reply = this.myAgent.receive(this.template);
				// 		if (proposal_reply != null) {
				// 			int performative = proposal_reply.getPerformative();
				// 			if (performative == ACLMessage.ACCEPT_PROPOSAL) {
				// 				System.out.println(local_name + ": получено ACCEPT_PROPOSAL");
				// 				// если сообщение было принято, то
				// 				// отправляем название предмета и
				// 				// удаляем предмет из списка
				// 				int index = (int)this.global_data.get(REQUESTED_WEIGHT_INDEX_KEY);
				// 				String item_name = items.get(index).getName();
				// 				ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
				// 				inform.addReceiver(proposal_reply.getSender());
				// 				inform.setContent(item_name);
				// 				System.out.println(local_name + ": отправка названия предмета");
				// 				this.myAgent.send(inform);

				// 				items.remove(index);
				// 			} else {
				// 				System.out.println(local_name + ": получено REJECT_PROPOSAL");
				// 			}
				// 			this.step = 1;
				// 		} else {
				// 			block();
				// 		}
				// 	}
				// }
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
						if (Math.abs((int)average - sum) < 2) {
							System.out.println(local_name + ": завершение поведения (близко к среднему) [" + Math.abs((int)average - sum) + "]");
							this.step = 0;
							break;
						}
						int current_weight = items.get(this.current_weight_index).getWeight();
						System.out.println(local_name + ": вычисление разности без текущего веса");
						int difference =  (int)average - (sum - current_weight);
						System.out.println(local_name + ": разница - " + difference);
						// заканчиваем, только тогда, когда вес предметов близок к среднему или
						// когда все предметы были просмотрены
						if (current_weight_index == items.size() - 1) {
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

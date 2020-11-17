import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.Arrays;

public class Tourist extends Agent {
	private TouristData tourist_data;
	private double average;

//	private boolean gotData = false;
	// (-1) - сумма весов меньше среднего среди рюкзаков
	// ( 0) - сумма весов равна среднему среди рюкзаков
	// ( 1) - сумма весов больше среднего среди рюкзаков
//	private int weight_comparison = 0;

	public double getAverage() {
		return this.average;
	}

	public int[] getWeights() {
		return this.tourist_data.getItems();
	}

	public void setWeights(int[] new_weights) {
		this.tourist_data.setItems(new_weights);
	}

	protected void setup() {
		this.tourist_data = new TouristData();
		this.average = 0.0;
		this.tourist_data.setName(getLocalName());

		// Получаем предметы и среднее среди всех рюкзаков
		Object[] args = this.getArguments();
		if (args.length == 0) return;
		this.average = (double)args[0];
		this.tourist_data.setItemObjects(Arrays.copyOfRange(args, 1, args.length));

		System.out.println(this.tourist_data.getName() + " появился");

/*		// Получение данных от менеджера (веса и средний вес)
		addBehaviour(new SimpleBehaviour(this) {
			private int state = 0;

			private String formatMessage(ACLMessage message) {
				return tourist_data.getName() +
						" : получил " +
						message.getContent() +
						" | " +
						message.getOntology() +
						" | от " +
						message.getSender().getLocalName();
			}

			@Override
			public void action() {
				ACLMessage message = this.myAgent.receive();
				if (message != null) {
					System.out.println(formatMessage(message));
					String ontology = message.getOntology();
					if (ontology.equals("weights")) {
						String[] separated = message.getContent().split(" ");
						int[] items = new int[separated.length];
						try {
							for (int i = 0; i < separated.length; ++i)
								items[i] = Integer.parseInt(separated[i]);
						} catch (NumberFormatException e) {
							System.out.println(tourist_data.getName() + " : ошибка при чтении весов");
							e.printStackTrace();
						}
						tourist_data.setItems(items);
						System.out.println(tourist_data.getName() + " : получено " + separated.length + " весов");
						this.state++;
					}
					if (ontology.equals("average")) {
						try {
							average = Double.parseDouble(message.getContent());
						} catch (NumberFormatException e) {
							System.out.println(tourist_data.getName() + " : ошибка при чтении среднего веса");
							e.printStackTrace();
						}
						System.out.println(tourist_data.getName() + " : получен средний вес");
						this.state++;
						gotData = true;
					}
				}
			}

			@Override
			public boolean done() {
				return this.state >= 2;
			}
		});*/

		// Регистрация и определения дальшейшего поведения туриста
		addBehaviour(new SimpleBehaviour(this) {
//			private boolean registered = false;
			private int step = 0;
			private int difference = 0;
			private String service_type = "";
			private int weight_comparison = 0;
			private int[] weights = new int[0];
			private ArrayList<Integer> additional_weights = new ArrayList<>();
			private int agents_count = 0;
			private int current_index = 0;

			private int getSum() {
				int sum = 0;
				for (int weight : this.weights) {
					sum += weight;
				}
				for (int weight : this.additional_weights) {
					sum += weight;
				}
				return sum;
			}

			private int searchWeight(int key) {
				for (int i = 0; i < this.weights.length; ++i) {
					if (weights[i] == key) {
						return i;
					}
				}
				return -1;
			}

			private void mergeArrays() {
				int[] new_weights = new int[this.weights.length + this.additional_weights.size()];
				System.arraycopy(this.weights, 0, new_weights, 0, this.weights.length);
				for (int i = 0; i < this.additional_weights.size(); ++i) {
					new_weights[i + this.weights.length] = this.additional_weights.get(i);
				}
				this.weights = new_weights;
				System.out.println("weights = " + Arrays.toString(this.weights));
			}

			private void deleteZeroWeights() {
				int targetIndex = 0;
				for (int sourceIndex = 0;  sourceIndex < this.weights.length;  sourceIndex++)
				{
					if( this.weights[sourceIndex] != 0 )
						this.weights[targetIndex++] = this.weights[sourceIndex];
				}
				int[] newArray = new int[targetIndex];
				System.arraycopy( this.weights, 0, newArray, 0, targetIndex );
				this.weights = newArray;
			}

			@Override
			public void action() {
				String name = this.myAgent.getLocalName();
				switch(this.step) {
					case 0 -> {
						this.step = 4;
						System.out.println(name + " : Данные получены, начало регистрации");
						this.weights = tourist_data.getItems();
						double weight_sum = Arrays.stream(weights).sum();
						if (weight_sum > average) {
							this.weight_comparison = 1;
							this.service_type = "overflow";
						} else if (weight_sum < average) {
							this.weight_comparison = -1;
							this.service_type = "lack";
						}

						if (this.weight_comparison != 0) {
							this.difference = Math.abs(getSum() - (int)average);
							DFUtilities.register(this.myAgent, this.service_type);
							System.out.println(name + ": Зарегистрирован как " + service_type);
							System.out.println(name + " : переход к шагу 1");
							this.step = 1;
						}
					}
					case 1 -> {
						if (this.weight_comparison == -1) {
							if (difference > 0) {
								System.out.println(name + " : разница больше нуля (" + difference + ")");
								System.out.println(name + " : поиск overflow туристов");

								// Находим туристов, у которых есть предмет с весом difference
								AID[] potential_helpers = DFUtilities.searchService(this.myAgent, "overflow");
								ACLMessage message = new ACLMessage(ACLMessage.CFP);
								this.agents_count = potential_helpers.length;
								for (AID helper : potential_helpers) {
									message.addReceiver(helper);
								}
								System.out.println(name + " : отправка разницы (" + difference + ")");
								message.setContent(Integer.toString(difference));
								message.setConversationId("helping");
								this.myAgent.send(message);
								this.step = 2;
							}
							if (difference == 0) {
								System.out.println(name + " : разница равна 0 (" + difference + ")");
								System.out.println(name + " : слияние массива " + Arrays.toString(this.weights) + " и списка " + this.additional_weights);
								System.out.println(name + " : удаление записи о lack регистрации");
								DFUtilities.deregister(this.myAgent);
								System.out.println(name + " : normal регистрация");
								DFUtilities.register(this.myAgent, "normal");
								System.out.println(name + " : переход к шагу 3");
								mergeArrays();
								this.step = 3;
							}
						} else if (this.weight_comparison == 1) {
							if ((int)average - getSum() > 0) {
								System.out.println(name + " : сумма весов " + ((int)average - getSum()) + "меньше среднего (" + average + "). Смена поведения");
								this.weight_comparison = -1;
								break;
							}

							ACLMessage request = this.myAgent.receive();
							if (request != null) {
								System.out.println(name + " : получено сообщение");
								if (request.getConversationId().equals("helping")) {
									System.out.println(name + " : запрос на вес " + request.getContent());
									ACLMessage reply = new ACLMessage(ACLMessage.PROPOSE);
									int index = searchWeight(Integer.parseInt(request.getContent()));
									if (index == -1) {
										System.out.println(name + " : такого веса нет");
										reply.setContent("-1");
									} else {
										System.out.println(name + " : есть такой вес");
										reply.setContent(request.getContent());
									}
									reply.setConversationId("helping");
									reply.addReceiver(request.getSender());
									System.out.println(name + " : отправка ответа");
									this.myAgent.send(reply);
								}

								if (request.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
									System.out.println(name + " : ответ на принятие запроса");
									int index = searchWeight(Integer.parseInt(request.getContent()));
									System.out.println(name + " : удаление веса " + this.weights[index]);
									this.weights[index] = 0;
									deleteZeroWeights();
								}
							}

							AID[] lack_agents = DFUtilities.searchService(this.myAgent, "lack");
							System.out.println(name + " : поиск lack туристов");
							this.agents_count = lack_agents.length;
							if (this.agents_count == 0) {
								System.out.println(name + " : lack туристов больше нет");
								System.out.println(name + " : переход к шагу 2");
								this.step = 2;
							}
						}
					}
					case 2 -> {
						if (this.weight_comparison == -1) {
							ACLMessage reply = this.myAgent.receive();
							if (reply != null) {
								if (reply.getPerformative() == ACLMessage.PROPOSE) {
									System.out.println(name + " : получено сообщение на запрос");
									if (reply.getContent().equals("-1")) {
										System.out.println(name + " : запрос отклонен");
										this.agents_count--;
									} else {
										System.out.println(name + " : получен вес " + reply.getContent());
										int new_weight = Integer.parseInt(reply.getContent());
										this.additional_weights.add(new_weight);

										System.out.println(name + " : ответ на принятие запроса");
										ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
										accept.setContent(Integer.toString(new_weight));
										accept.addReceiver(reply.getSender());
										accept.setConversationId("accepting");
										this.myAgent.send(accept);

										this.difference = Math.abs(getSum() - (int)average);
										System.out.println(name + " : разница теперь - " + this.difference);
										System.out.println(name + " : переход к шагу 1");
										this.step = 1;
									}
								}
							}

							if (this.agents_count == 0) {
								System.out.println(name + " : все запросы отклонены. Уменьшение разницы");
								this.difference--;
								System.out.println(name + " : Переход к шагу 1");
								this.step = 1;
							}
						} else if (this.weight_comparison == 1) {
							if (this.current_index == this.weights.length || Math.abs(this.difference) < 2) {
								System.out.println(name + " : текущий индекс - " + this.current_index);
								System.out.println(name + " : разница - " + this.difference);
								System.out.println(name + " : удаление лишних нулей, если они есть");
								deleteZeroWeights();
								System.out.println(name + " : переход к шагу 4");
								this.step = 4;
								break;
							}
							this.difference = Math.abs(getSum() - this.weights[this.current_index] - (int)average);
							System.out.println(name + " : разница без веса с текущим индексом - " + this.difference);
							System.out.println(name + " : отправка запроса всем normal туристам");
							AID[] potential_helpers = DFUtilities.searchService(this.myAgent, "normal");
							ACLMessage message = new ACLMessage(ACLMessage.CFP);
							this.agents_count = potential_helpers.length;
							for (AID helper : potential_helpers) {
								message.addReceiver(helper);
							}
							message.setContent(Integer.toString(difference));
							this.myAgent.send(message);
							System.out.println(name + " : переход к шагу 3");
							this.step = 3;
						}
					}

					case 3 -> {
						// Обмен предметами
						if (weight_comparison == -1) {
							ACLMessage message = this.myAgent.receive();
							if (message != null) {
								int performative = message.getPerformative();
								System.out.println(name + " : получено сообщение");
								if (performative == ACLMessage.CFP) {
									System.out.println(name + " : получен запрос на обмен с весом " + message.getContent());
									int searched_weight = Integer.parseInt(message.getContent());
									int index = searchWeight(searched_weight);
									ACLMessage reply = new ACLMessage(ACLMessage.PROPOSE);
//									reply.setContent(message.getContent());
									reply.addReceiver(message.getSender());

									if (index == -1) {
										System.out.println(name + " : такого веса нет");
										reply.setContent("-1");
									} else {
										System.out.println(name + " : удаление веса из своего рюкзака");
										reply.setContent(message.getContent());
										this.weights[index] = -1;
									}
									System.out.println(name + " : ответ на запрос");
									this.myAgent.send(reply);
								}
								if (performative == ACLMessage.ACCEPT_PROPOSAL) {
									System.out.println(name + " : получение ответа на ответ на запрос");
									int index = searchWeight(-1);
									this.weights[index] = Integer.parseInt(message.getContent());
									System.out.println(name + " : получен вес " + this.weights[index]);
								}
							}
							AID[] agents = DFUtilities.searchService(this.myAgent, "overflow");
							if (agents.length == 0) {
								System.out.println(name + " : нет overflow туристов");
								System.out.println(name + " : переход к шагу 4");
								this.step = 4;
							}
						} else if (weight_comparison == 1) {
							ACLMessage reply = this.myAgent.receive();
							if (reply != null) {
								if (reply.getPerformative() == ACLMessage.PROPOSE) {
									System.out.println(name + " : получено сообщение с ответом на запрос");
									if (reply.getContent().equals("-1")) {
										System.out.println(name + " : запрос отклонен");
										this.agents_count--;
									} else {
										System.out.println(name + " : запрос принят");
//									int new_weight = Integer.parseInt();
//									this.additional_weights.add(new_weight);

										ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
										accept.setContent(Integer.toString(this.weights[this.current_index]));
										System.out.println(name + " : отправка веса " + this.weights[this.current_index]);
										accept.addReceiver(reply.getSender());
										accept.setConversationId("accepting");
										this.myAgent.send(accept);

										this.weights[this.current_index] = Integer.parseInt(reply.getContent());
										System.out.println(name + " : новый вес " + this.weights[this.current_index]);
										System.out.println(name + " : переход к шагу 2");
										this.step = 2;
									}
								}
							}

							if (this.agents_count == 0) {
								System.out.println(name + " : все запросы отклонены");
								this.current_index++;
								System.out.println(name + " : текущий индекс " + this.current_index);
								System.out.println(name + " : переход к шагу 2");
								this.step = 2;
							}
						}
					}
					case 4 -> {
						System.out.println(name + " : удаление записи о регистрации");
						DFUtilities.deregister(this.myAgent);
						System.out.println(name + " : конец работы");
						this.step = 5;
					}
				}


				// Проводим регистрацию, только если есть либо нехватка, либо переполение
				/*if (weight_comparison != 0) {
					DFUtilities.register(this.myAgent, service_type);
					this.registered = true;
					System.out.println(name + ": Зарегистрирован как " + service_type);

					// Как только туристы зарегистрировались, добавляется соответствующее поведение
					if (weight_comparison == 1)
						this.myAgent.addBehaviour(new SimpleBehaviour(this.myAgent) {
							private int step = 0;
							private int[] weights;
							private double average;

							// смещение от начала отсортированного массива
							// (для тех агетов, у которых сумма весов больше
							// среднего веса среди всех рюкзаков)
							private int offset = 0;
							private boolean tight = false;

							private int getSum() {
								int sum = 0;
								for (int i = this.offset; i < this.weights.length; ++i)
									sum += this.weights[i];
								return sum;
							}

							@Override
							public void action() {
								// Ожидание запроса от другого туриста
								// Получаем список туристов из DF, которым нужны предметы
								// Если же все туристы, которым нужны предметы, удалили свои записи из DF, тогда
								// заканчиваем работу
								switch (this.step) {
									case 0 -> {
										this.weights = ((Tourist) this.myAgent).getWeights();
										Arrays.sort(this.weights);
										this.average = ((Tourist) this.myAgent).getAverage();
										this.step++;
										System.out.println(name + " : сортированные веса - " + Arrays.toString(weights));
									}
									case 1 -> {
										if (!tight) {
											// Проверка на допустимость удаления предмета
											// Если же турист не может отдать предмет другому туристу
											// так, чтобы сумма весов его предметов была больше среднего, то
											// он должен закончить работу и удалить запись о регистрации с DF
											int sum = getSum();
											int min = this.weights[this.offset];
											////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
											////////////////////////////////////////////////////////////////////////////////////////////////////////////
											///////////////////
											if (sum - min < (int)(this.average * 0.9)) {
												System.out.println(name + " : больше не может делиться (" + sum + "," + this.average + ")");
												tight = true;
												System.out.println(name + " : удаление записи о регистрации");
												DFUtilities.deregister(this.myAgent);
											}
										}
										ACLMessage help_message = this.myAgent.receive();
										if (help_message != null) {
											// Обработка только запросов (PROPOSAL)
											if (help_message.getPerformative() == ACLMessage.PROPOSE) {
												System.out.println(name + " : получен запрос");
												ACLMessage reply = help_message.createReply();
												// Если турист не может отдать предмет, то он отклоняет запрос
												// иначе отправляем вес предмета и удаляем его у себя
												if (tight) {
													reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
													System.out.println(name + " : отклонение запроса");
												} else {
													reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
													reply.setContent(Integer.toString(weights[offset]));
													System.out.println(name + " : принятие запроса (" + weights[offset] + ")");
													// "Удаляем" предмет
													offset++;
													System.out.println(name + " : предметов отдано" + offset);
												}
												this.myAgent.send(reply);
											}
										}
										AID[] lack_tourists = DFUtilities.searchService(this.myAgent, "lack");
										if (lack_tourists.length == 0) {
											// Копируем веса
											int[] new_weights = Arrays.copyOfRange(this.weights, this.offset, this.weights.length);
											System.out.println(name + " : не осталось lack туристов. Отправка весов - " + Arrays.toString(new_weights));
											((Tourist)this.myAgent).setWeights(new_weights);
											this.step++;
										}
									}
								}
							}

							@Override
							public boolean done() {
								if (this.step >= 2) {
									doDelete();
									return true;
								}
								return false;
							}
						});
					else if (weight_comparison == -1)
						this.myAgent.addBehaviour(new SimpleBehaviour(this.myAgent) {
							private int[] weights;
							private double average;
							private ArrayList<Integer> additional_weights;
							// массив с туристами, к которым можно обратиться
							private AID[] potential_helpers;
							private int current_helper = 0;
							private int step = 0;

							private int getSum() {
								int sum = 0;
								for (int weight : this.weights) {
									sum += weight;
								}
								for (int weight : this.additional_weights) {
									sum += weight;
								}
								return sum;
							}

							@Override
							public void action() {
								switch (this.step) {
									case 0 -> {
										this.weights = ((Tourist)this.myAgent).getWeights();
										this.average = ((Tourist)this.myAgent).getAverage();
										this.additional_weights = new ArrayList<>();
										this.step++;
										System.out.println(name + " : инициализирован");
									}
									case 1 -> {
										// Находим туристов с лишними предметами
										// (к этому моменту все туристы должны быть зарегистрированы)
										this.potential_helpers = DFUtilities.searchService(this.myAgent, "overflow");
										this.step++;
									}
									case 2 -> {
										// Если мы опросили всех туристов, то заканчиваем работу
										if (this.current_helper == this.potential_helpers.length) {
											System.out.println(name + " : все туристы были опрошены (" + this.potential_helpers.length + ")");
											this.step = 4;
											break;
										}
										// Если же у туриста уже набралось достаточное количество предметов,
										// то заканчиваем работу
										int sum = getSum();
										if (sum >= average) {
											System.out.println(name + " : набралось достаточное количество предметов ("  + sum + ")");
											this.step = 4;
											break;
										}

										ACLMessage help_message = new ACLMessage(ACLMessage.PROPOSE);
										AID helper = this.potential_helpers[this.current_helper];
										help_message.addReceiver(helper);
										this.myAgent.send(help_message);
										System.out.println(name + " : отправка запроса");
										this.step = 3;
									}
									case 3 -> {
										ACLMessage reply = this.myAgent.receive();
										if (reply != null) {
											int performative = reply.getPerformative();
											// Если турист послал вес, то добавляем в массив дополнительных весов
											// и возвращаемся на шаг назад, чтобы послать ещё один запрос текущему
											// туристу
											if (performative == ACLMessage.ACCEPT_PROPOSAL) {
												try {
													int new_weight = Integer.parseInt(reply.getContent());
													this.additional_weights.add(new_weight);
													System.out.println(name + " : запрос принят (" + new_weight + ")");
													System.out.println(name + " : все полученные веса " + this.additional_weights.toString());
												} catch (NumberFormatException e) {
													System.out.println(this.myAgent.getName() + " : Получено некорректное значение веса");
													e.printStackTrace();
												}
												this.step = 2;
											// Если же турист отказался, то двигаемся к следующему туристу
											// и возвращаемся на шаг назад
											} else if (performative == ACLMessage.REJECT_PROPOSAL) {
												System.out.println(name + " : запрос отклонен от " + this.current_helper);
												this.current_helper++;
												this.step = 2;
											}
										}
									}
									case 4 -> {
										System.out.println(name + " : удаление записи о регистрации");
										DFUtilities.deregister(this.myAgent);
										int[] new_weights = new int[this.weights.length + this.additional_weights.size()];
										System.arraycopy(this.weights, 0, new_weights, 0, this.weights.length);
										for (int i = 0; i < this.additional_weights.size(); ++i) {
											new_weights[i + this.weights.length] = this.additional_weights.get(i);
										}
										System.out.println(name + " : отправка весов - " + Arrays.toString(new_weights));
										((Tourist)this.myAgent).setWeights(new_weights);
										this.step++;
									}
								}
							}

							@Override
							public boolean done() {
								if (this.step >= 5) {
									doDelete();
									return true;
								}
								return false;
							}
						});
				}*/
			}

			@Override
			public boolean done() {
				if (this.step == 5) {
					tourist_data.setItems(this.weights);
					doDelete();
					return true;
				}
				return false;
			}
		});

	}

	// Отправка весов менеджеру
	protected void takeDown() {
		// Преобразование массива весов в строку
		StringBuilder content = new StringBuilder();
		int[] weights = this.getWeights();
		for (int weight : weights)
			content.append(weight).append(" ");
		System.out.println(getLocalName() + " : подготовка сообщения - " + content.toString());

		// Находим менеджера
		System.out.println(getLocalName() + " : поиск менеджера");
		AID manager = DFUtilities.getService(this,"manager");

		// Отправляем ему новые веса
		ACLMessage result_message = new ACLMessage(ACLMessage.INFORM);
		result_message.addReceiver(manager);
		result_message.setContent(content.toString());
		System.out.println(getLocalName() + " : отправка весов менеджеру");
		this.send(result_message);
	}
}

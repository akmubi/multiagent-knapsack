import jade.core.AID;
import jade.core.Agent;
import java.util.Arrays;
import java.util.ArrayList;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;

public class Tourist extends Agent {
	protected void setup() {
		// получаем аргументы
		Object[] args = getArguments();
		// среднее среди всех предметов
		double average = (double)args[0];
		// веса предметов
		ArrayList<TouristItem> items = new ArrayList<>(Arrays.asList((TouristItem[]) args[1]));

		int tourist_id = (int)args[2];

		 DFUtilities.register(this, "tourist");

		 addBehaviour(new TouristBehaviour(this, items, average, tourist_id));
	}
}

class TouristBehaviour extends SimpleBehaviour {
	private int step;
	private int tourists_count;
	private AID[] current_tourists;
	private final int tourist_id;
	private final double average;
	private final DataStore global_data;
	private final ArrayList<TouristItem> items;
	private final ArrayList<ACLMessage> replies;
	private boolean done;
	private int laps_count;

	public TouristBehaviour(Agent agent, ArrayList<TouristItem> items, double average, int tourist_id) {
		super(agent);
		this.step = 1;
		this.done = false;
		this.items = items;
		this.average = average;
		this.replies = new ArrayList<>();
		this.laps_count = 0;
		this.tourist_id = tourist_id;
		this.global_data = getDataStore();
		this.tourists_count = 0;
		this.current_tourists = new AID[0];
	}

	private void println(String message) {
		System.out.println(this.myAgent.getLocalName() + ": " + message);
	}

	private int parseInteger(String content) {
		int value = -1;
		try {
			value = Integer.parseInt(content);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return value;
	}

	private AID[] getTourists() {
		AID[] tourists = DFUtilities.searchService(this.myAgent, "tourist");
		AID[] to_return = new AID[tourists.length - 1];
		for (int i = 0, j = 0; i < tourists.length; ++i) {
			if (!tourists[i].getName().equals(this.myAgent.getName())) {
				to_return[j++] = tourists[i];
			}
		}
		return to_return;
	}

	private void skipLap(AID[] tourists) {
		ACLMessage skip_message = new ACLMessage(ACLMessage.CANCEL);
		for (AID tourist : tourists) {
			skip_message.addReceiver(tourist);
		}
		println("отправка команды на завершение круга " + tourists.length + " туристам");
		this.myAgent.send(skip_message);
	}

	@Override
	public void action() {
		switch (this.step) {
			case 1 -> {
				AID[] tourists = getTourists();
				this.tourists_count = tourists.length;
				println("туристов на данный момент - " + this.tourists_count);

				AID[] done_tourists = DFUtilities.searchService(this.myAgent, "done");
				if (this.done) {
					if (done_tourists.length == this.tourists_count + 1) {
						this.step = 0;
						break;
					}
				} else {
					println("done туристов - " + done_tourists.length);
					if (done_tourists.length == this.tourists_count) {
						this.step = 0;
						break;
					}
				}

				// вычисление суммы предметов
				int sum = TouristItem.getSum(this.items);
				println("текущие предметы - " + TouristItem.toString(this.items) + " (" + sum + ")");
				if (sum == (int)this.average && !this.done) {
					println("текущая сумма равна среднему. Регистрация как done");
					DFUtilities.addService(this.myAgent, "done");
					this.done = true;
				}

				if (this.laps_count % (this.tourists_count + 1) == this.tourist_id) {
					// отправка запросов
					println("отправка запросов (laps = " + this.laps_count + ", index = " + this.tourist_id + ")");
					if (this.done) {
						this.step = 6;
					} else {
						this.step = 2;
					}
					this.current_tourists = tourists;
				} else {
					// обработка запросов
					println("обработка запросов (laps = " + this.laps_count + ", index = " + this.tourist_id + ")");
					this.step = 7;
				}
			}
			case 2 -> {
				TouristItem current_item = this.items.get(0);
				int sum = TouristItem.getSum(this.items);
				int difference = (int)this.average - (sum - current_item.getWeight());
				println("текущая разница - " + difference);

				println("отправка запросов " + this.tourists_count + " туристам");
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (AID tourist : this.current_tourists) {
						cfp.addReceiver(tourist);
				}
				if (difference < 0) {
					cfp.setConversationId("min");
					difference = -difference;
				} else {
					cfp.setConversationId("max");
				}
				cfp.setContent(Integer.toString(difference));
				this.myAgent.send(cfp);
				this.step = 3;
			}
			case 3 -> {
				ACLMessage message = this.myAgent.receive();
				if (message != null) {
					this.replies.add(message);
					println("получено сообщение (" + this.replies.size() + "/" + this.tourists_count + ") от " + message.getSender().getLocalName());
				} else {
					block();
				}

				if (this.replies.size() == this.tourists_count) {
					println("получены все сообщения");
					this.step = 4;
				}
			}
			case 4 -> {
				println("получены все ответы на запрос (PROPOSE / REFUSE)");
				int sum = TouristItem.getSum(this.items);
				int difference = (int)average - (sum - this.items.get(0).getWeight());

				AID best_sender = null;
				int best_weight = (difference < 0) ? this.items.get(0).getWeight() : -1;
				for (ACLMessage reply : this.replies) {
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						int reply_weight = parseInteger(reply.getContent());
						if (difference < 0) {
							if (reply_weight < best_weight) {
								best_weight = reply_weight;
								best_sender = reply.getSender();
							}
						} else {
							if (reply_weight > best_weight) {
								best_weight = reply_weight;
								best_sender = reply.getSender();
							}
						}
					}
				}

				if (best_sender == null) {
					this.laps_count++;
					ACLMessage skip_message = new ACLMessage(ACLMessage.CANCEL);
					for (ACLMessage reply : this.replies) {
						skip_message.addReceiver(reply.getSender());
					}
					this.myAgent.send(skip_message);
					this.step = 1; 
				} else {
					println("лучший вес - " + best_weight + " от " + best_sender.getLocalName());
					ACLMessage answer;
					for (ACLMessage reply : this.replies) {
						if (reply.getPerformative() == ACLMessage.PROPOSE) {
							if (best_sender.getName().equals(reply.getSender().getName())) {
								answer = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
							} else {
								answer = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
							}
							answer.addReceiver(reply.getSender());
							this.myAgent.send(answer);
						}
					}
					this.step = 5;
				}
				this.replies.clear();
			}
			case 5 -> {
				ACLMessage inform = this.myAgent.receive();
				if (inform != null) {
					int performative = inform.getPerformative();
					if (performative == ACLMessage.INFORM) {
						try {
							TouristItem new_item = TouristItem.parseTouristItem(inform.getContent());
							println("получен новый предмет " + TouristItem.toString(new_item) + ". Добавление в список");
							this.items.add(new_item);
							// отправка текущего веса
							ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
							reply.addReceiver(inform.getSender());
							reply.setContent(TouristItem.toString(this.items.get(0)));
							println("отправка текущего веса (" + TouristItem.toString(this.items.get(0)) + ") для " + inform.getSender().getLocalName());
							this.myAgent.send(reply);

							this.items.remove(0);
							this.step = 6;
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				} else {
					block();
				}
			}
			case 6 -> {
				skipLap(this.current_tourists);
				this.laps_count++;
				this.step = 1;
			}
			case 7 -> {
				ACLMessage message = this.myAgent.receive();
				if (message != null) {
					int performative = message.getPerformative();
					AID sender = message.getSender();
					String conversation_id = message.getConversationId();
					String content = message.getContent();
					if (performative == ACLMessage.CFP) {
						if (this.done) {
							ACLMessage reply = new ACLMessage(ACLMessage.REFUSE);
							reply.addReceiver(message.getSender());
							this.myAgent.send(reply);
							break;
						}
						println("получен CFP запрос от " + sender.getLocalName());
						int weight = parseInteger(content);
						println("запрашиваемый вес - " + weight);
						int index = -1;
						if (conversation_id.equals("min")) {
							index = TouristItem.searchMinNotExceed(this.items, weight);
						} else if (conversation_id.equals("max")) {
							index = TouristItem.searchMaxNotExceed(this.items, weight);
						}

						ACLMessage reply;
						if (index == -1) {
							println("подходящий вес не был найден. Отклонение (REFUSE)");
							reply = new ACLMessage(ACLMessage.REFUSE);
						} else {
							TouristItem current_item = this.items.get(index);
							int current_weight = current_item.getWeight();
							println("подходящий вес " + TouristItem.toString(current_item) + " с индексом " + index + " был найден. Отправка (PROPOSE)");

							reply = new ACLMessage(ACLMessage.PROPOSE);
							reply.setContent(Integer.toString(current_weight));

							this.global_data.put(this.myAgent.getName() + sender.getName(), index);
						}
						reply.addReceiver(sender);
						this.myAgent.send(reply);
					} else if (performative == ACLMessage.ACCEPT_PROPOSAL) {
						int index = (int)this.global_data.get(this.myAgent.getName() + sender.getName());
						TouristItem item = this.items.get(index);
						println("получено одобрение (ACCEPT_PROPOSAL). Отправка предмета - " + TouristItem.toString(item));
						ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
						inform.addReceiver(sender);
						inform.setContent(TouristItem.toString(item));
						this.myAgent.send(inform);
						println("удаление веса предмета");
						this.global_data.remove(this.myAgent.getName() + sender.getName());
						this.items.remove(index);
					} else if (performative == ACLMessage.CANCEL) {
						println("завершение круга...");
						this.laps_count++;
						this.step = 1;
					} else if (performative == ACLMessage.INFORM) {
						try {
							println("получен предмет " + message.getContent() + ". Добавление");
							TouristItem new_item = TouristItem.parseTouristItem(message.getContent());
							this.items.add(new_item);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
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
		println("отправка предметов серверу...");
		TouristData data = new TouristData(this.myAgent.getLocalName(), items);
		ACLMessage results_message = new ACLMessage(ACLMessage.INFORM);
		results_message.setConversationId("results");
		results_message.setContent(TouristData.toString(data));
		results_message.addReceiver(new AID("server", AID.ISLOCALNAME));
		this.myAgent.send(results_message);
		println("завершение работы...");
		DFUtilities.deregister(this.myAgent);
		this.myAgent.doDelete();

		return super.onEnd();
	}

}
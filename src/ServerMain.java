import jade.core.AID;
import jade.core.Agent;
import jade.core.AgentDescriptor;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.AMSService;
import jade.domain.FIPAAgentManagement.AMSAgentDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.MessageTemplate.MatchExpression;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class ServerMain extends Agent {
	protected void setup() {
		String local_name = getLocalName();
		Object[] args = getArguments();

		// файл с именами туристов
		String input_filename = args[0].toString();
		System.out.println(local_name + ": название файла с именами - " + input_filename);

		// чтение имен туристов (и соответственно их количества)
		ArrayList<String> tourist_names = readTouristNames(input_filename);
		System.out.println(local_name + ": имена - " + tourist_names.toString());

		addBehaviour(new HandleClientsBehaviour(this, tourist_names));
	}

	// чтение имён туристов из файла
	private ArrayList<String> readTouristNames(String input_filename) {
		/*
		*   Формат файла:
		*   имя 1
		*   имя 2
		*   ...
		*   имя n
		* */
		ArrayList<String> names = new ArrayList<>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader(input_filename));
			String line;
			try {
				while ( (line = reader.readLine()) != null ) {
					names.add(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			doDelete();
		}
		return names;
	}
}

class HandleClientsBehaviour extends CyclicBehaviour {
	private final ArrayList<String> tourist_names;
	private final MessageTemplate client_message_template;

	public HandleClientsBehaviour(Agent agent, ArrayList<String> tourist_names) {
		super(agent);
		this.tourist_names = tourist_names;

		// описание шаблона для получения сообщения от клиента
		MessageTemplate.MatchExpression expression = (MessageTemplate.MatchExpression) message -> {
			int performative = message.getPerformative();
			String conversation_Id = message.getConversationId();
			if (conversation_Id == null)
				return false;
			return  performative == ACLMessage.INFORM &&
					conversation_Id.equals("connection");
		};
		this.client_message_template =  new MessageTemplate(expression);
	}

	@Override
	public void action() {
		String local_name = this.myAgent.getLocalName();
		// ожидание получения сообщения от клиента
		ACLMessage connection_message = this.myAgent.receive(this.client_message_template);
		if (connection_message != null) {
			AID current_client = connection_message.getSender();
			System.out.println(local_name + ": получен запрос на подключение от " + current_client.getName());

			// создание отдельного поведения для этого клиента
			System.out.println(local_name + ": создание отдельного поведения");
			this.myAgent.addBehaviour(new SingleClientBehaviour(this.myAgent, current_client, this.tourist_names));

			// отправка сообщения о подлкючении
			System.out.println(local_name + ": отправка сообщения о успешном подключении");
			ACLMessage connected_message = new ACLMessage(ACLMessage.INFORM);
			connected_message.setContent("connected");
			connected_message.addReceiver(current_client);
			this.myAgent.send(connected_message);
		} else {
			// добавление в очередь для заблокированных агентов и
			// ожидание следующего сообщения
			block();
		}
	}
}

class SingleClientBehaviour extends SimpleBehaviour {
	private int step;
	private MessageTemplate template;
	private ArrayList<TouristItem> items;

	private final AID client;
	private final String local_name;
	private final ArrayList<String> tourist_names;
	private final ArrayList<TouristData> tourist_data;

	public SingleClientBehaviour(Agent agent, AID client, ArrayList<String> tourist_names) {
		super(agent);
		this.step = 1;
		this.client = client;
		this.tourist_data = new ArrayList<>();
		this.items = new ArrayList<>();
		this.tourist_names = tourist_names;
		this.local_name = this.myAgent.getLocalName();
		// описание шаблона для получения сообщений от клиента
		MatchExpression expression = (MatchExpression) message -> {
			int performative = message.getPerformative();
			String sender = message.getSender().getName();
			return  performative == ACLMessage.INFORM &&
					sender.equals(this.client.getName());
		};
		this.template =  new MessageTemplate(expression);
	}

	@Override
	public void action() {
		switch (this.step) {
			case 1 -> {
				// получение предметов
				System.out.println(local_name + ": получение предметов...");
				ACLMessage items_message = this.myAgent.receive(this.template);

				if (items_message != null) {
					try {
						this.items = TouristItem.parseTouristItems(items_message.getContent());
						System.out.println(local_name + ": получено " + this.items.size() + " предметов");
						System.out.println(local_name + ": предметы - " + items_message.getContent());
						// unzipping
						this.items = TouristItem.unzip(this.items);
						System.out.println(local_name + ": предметы после 'распаковки' - " + TouristItem.toString(this.items));
					} catch (Exception e) {
						e.printStackTrace();
						this.step = 0;
						break;
					}

					// переходим к следующему шагу
					this.step = 2;
				} else {
					block();
				}
			}
			case 2 -> {
				// Распределение весов
				System.out.println(local_name + ": начальное распределение весов");
				int items_count = this.items.size();
				int tourists_count = this.tourist_names.size();
				int items_per_tourist = items_count / tourists_count;

				Object[] tourist_args = new Object[tourists_count];

				if (items_per_tourist == 0) {
					for (int i = 0; i < items_count; ++i) {
						TouristItem[] tourist_items = new TouristItem[1];
						tourist_items[0] = this.items.get(i);
						tourist_args[i] = tourist_items;
					}
					for (int i = items_count; i < tourists_count; ++i) {
						tourist_args[i] = new TouristItem[0];
					}
				} else {
					for (int i = 0; i < tourists_count; ++i) {
						int tourist_items_count = items_per_tourist;

						// последнему туристу достаётся остаток предметов
						if (i == tourists_count - 1) {
							tourist_items_count += items_count % tourists_count;
						}

						TouristItem[] tourist_items = new TouristItem[tourist_items_count];
						for (int j = 0; j < tourist_items_count; ++j) {
							tourist_items[j] = this.items.get(i * items_per_tourist + j);
						}
						tourist_args[i] = tourist_items;
					}
				}

				// Создание и запуск агентов (туристов)
				try {
					AgentContainer container = this.myAgent.getContainerController();
					System.out.println(local_name + ": вычисление среднего...");
					double average = TouristItem.calculateAverage(this.items, tourists_count);
					System.out.println(local_name + ": среднее - " + average);

					System.out.println(local_name + ": запуск " + tourists_count + " агентов");
					for (int i = 0; i < tourists_count; ++i) {

						// костыль
						Object[] args = new Object[2];
						args[0] = average;
						args[1] = tourist_args[i];

						// создание агента
						String tourist_name = this.tourist_names.get(i);
						AgentController tourist = container.createNewAgent(tourist_name, "Tourist", args);
						// запуск агента
						tourist.start();
					}
				} catch (Exception e) {
					e.printStackTrace();
					this.step = 0;
					break;
				}


				// переход к следующему шагу
				this.step = 3;
			}
			case 3 -> {
				// подготовка шаблона сообщения для получения результатов
				MatchExpression expression = (MatchExpression) message -> {
					int performative = message.getPerformative();
					String conversation_Id = message.getConversationId();
					String sender_name = message.getSender().getLocalName();
					boolean is_tourist = this.tourist_names.contains(sender_name);
					return  performative == ACLMessage.INFORM &&
							is_tourist &&
							conversation_Id.equals("results");
				};
				this.template = new MessageTemplate(expression);
				this.step = 4;
				System.out.println(local_name + ": ожидание сообщения от агентов...");
			}
			case 4 -> {
				// получения сообщения от агентов
				ACLMessage tourist_message = this.myAgent.receive(this.template);
				if (tourist_message != null) {
					System.out.println(local_name + ": получено сообщение от " + tourist_message.getSender().getLocalName());
					String content = tourist_message.getContent();
					String sender_name = tourist_message.getSender().getLocalName();
					try {
						TouristData data = TouristData.parseTouristData(content);
						this.tourist_data.add(data);
					} catch (Exception e) {
						e.printStackTrace();
						this.step = 0;
						break;
					}
				} else {
					block();
				}

				if (this.tourist_data.size() == this.tourist_names.size()) {
					// если получены все количества то,
					// переходим к следующему шагу
					System.out.println(local_name + ": получены сообщения от всех агентов");
					this.step = 5;
				}
			}
			case 5 -> {
				// сообщения клиенту о том, начале отправки результатов
				System.out.println(local_name + ": отправка результатов " + this.client.getName());
				ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
				inform.addReceiver(this.client);
				inform.setConversationId("results");
				// отправляем количество туристов
				inform.setContent(Integer.toString(this.tourist_data.size()));
				this.myAgent.send(inform);

				// отправка результатов клиенту
				ACLMessage results = new ACLMessage(ACLMessage.INFORM);
				results.addReceiver(this.client);

				for (TouristData data : this.tourist_data) {
					// zipping
					TouristData zipped = data.getZipped();

					// [Имя туриста] [Название предмета 1] [Вес предмета 1] [Количество предмета 1] ...
					results.setContent(TouristData.toString(zipped));
					this.myAgent.send(results);
				}

				// завершение работы
				System.out.println(local_name + ": конец обработки клиента " + this.client.getName());
				this.step = 0;
			}
		}
	}

	@Override
	public boolean done() {
		return this.step == 0;
	}
}

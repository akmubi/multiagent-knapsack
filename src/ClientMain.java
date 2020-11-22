import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;

public class ClientMain extends Agent {

	protected void setup() {
		// Получение названий входного и выходного файла
		Object[] args = getArguments();

		// Чтение данных из файла
		String input_filename = args[0].toString();
		String output_filename = args[1].toString();
		IOUtility io_utility = new IOUtility(input_filename, output_filename);
		ArrayList<TouristItem> items = io_utility.readItems();

		// Подсчёт количества предметов
		int item_count = items.size();

		addBehaviour(new SimpleBehaviour(this) {
			private int step = 1;

			@Override
			public void action() {
				switch (step) {
					case 1 -> {
						// Связь с сервером
						ACLMessage connection_message = new ACLMessage(ACLMessage.INFORM);
						connection_message.addReceiver(new AID("server", false));
						connection_message.setContent("connection");
						connection_message.setConversationId("connection");
						this.myAgent.send(connection_message);
						this.step = 2;
					}
					case 2 -> {
						
					}
				}
			}

			@Override
			public boolean done() {
				return false;
			}
		});

	}

	protected void takeDown() {

	}
}


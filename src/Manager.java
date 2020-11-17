import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

import java.io.*;
import java.util.Scanner;

public class Manager extends Agent {
	private TouristData[] tourist_data = new TouristData[0];

	private double calculateAverage() {
		double average = 0.0;
		for (TouristData data : this.tourist_data) {
			int[] items = data.getItems();
			for (int item : items) average += item;
		}
		average /= this.tourist_data.length;
		return average;
	}

	// Подготовка весов и среднего к отправке туристу
	private Object[] prepareArguments(TouristData data, double average) {
		int[] items = data.getItems();
		Object[] args = new Object[items.length + 1];
		args[0] = average;
		for (int i = 0; i < items.length; ++i) {
			args[i + 1] = items[i];
		}
		return args;
	}

	protected void setup() {
		tourist_data = this.input("files/input_file.txt");

		for (TouristData data : tourist_data)
			System.out.println(getLocalName() + " : " + data.toString());

		double average = this.calculateAverage();

		try {
			AgentContainer container = getContainerController();
			for (TouristData data : tourist_data) {
				Object[] args = this.prepareArguments(data, average);
				AgentController agent = container.createNewAgent(data.getName(), "Tourist", args);
				agent.start();
			}
		} catch (Exception e) {
			System.out.println(getLocalName() + " : ошибка при создании агентов");
			e.printStackTrace();
		}

/*
		// передача значения среднего веса каждому туристу
		addBehaviour(new Broadcast(tourist_data));*/

		// Регистрация в DF
		DFUtilities.register(this, "manager");

		// получение результатов от туристов
		 addBehaviour(new SimpleBehaviour(this) {
			private int received = 0;

		 	@Override
			 public void action() {
		 		ACLMessage result_message = receive();
		 		if (result_message != null) {
		 			System.out.println(this.myAgent.getLocalName() + " : получил сообщение - " + result_message.getContent() + "(" + (this.received + 1) + "/" + tourist_data.length + ")");
				    String[] separated = result_message.getContent().split(" ");
				    int[] weights = new int[separated.length];
				    try {
					    for (int i = 0; i < separated.length; ++i)
						    weights[i] = Integer.parseInt(separated[i]);
				    } catch (NumberFormatException e) {
					    System.out.println(this.myAgent.getName() + " : ошибка при чтении весов");
					    e.printStackTrace();
				    }
				    // Переписываем данные о туристах
				    tourist_data[this.received].setItems(weights);
				    tourist_data[this.received].setName(result_message.getSender().getLocalName());
		 			this.received++;
			    }
			 }

			 @Override
			 public boolean done() {
		 		if (this.received == tourist_data.length) {
		 			System.out.println(this.myAgent.getLocalName() + " : получены все сообщения - " + this.received);
		 			doDelete();
		 			return true;
			    }
		 		return false;
			 }
		 });
	}

	// Выполняется до окончания работы менеджера
	protected void takeDown() {
		try {
			System.out.println(getLocalName() + " : удаление записи о регистрации");
			DFUtilities.deregister(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println(getLocalName() + " : сохранение весов в файл");
		output("files/output_file.txt", this.tourist_data);
	}

	// чтение данных из файла
	// (количество туристов, их имена, веса предметов)
	private TouristData[] input(String input_filename) {
		TouristData[] tourist_data = new TouristData[0];
		try {
			File file_object = new File(input_filename);
			Scanner my_reader = new Scanner(file_object);

			// Чтение количества туристов
			int tourist_count = -1;
			try {
				tourist_count = Integer.parseInt(my_reader.nextLine());
			} catch (NumberFormatException e) {
				System.out.println("Ошибка при чтении количества туристов");
				e.printStackTrace();
			}

			// Чтение имен и весов предметов
			tourist_data = new TouristData[tourist_count];
			for (int i = 0; i < tourist_count; ++i){
				String line = my_reader.nextLine();
				String[] separated = line.split(" ");
				if (separated.length > 0) {
					// проверяем являются ли строки - числами
					// и затем записываем в временный массив items
					int[] items = new int[separated.length - 1];
					try {
						for (int j = 1; j < separated.length; ++j) {
							items[j - 1] = Integer.parseInt(separated[j]);
						}
					} catch (NumberFormatException e) {
						System.out.println("Ошибка при чтении весов предметов");
						e.printStackTrace();
					}

					// Записываем имя туриста и веса предметов
					tourist_data[i] = new TouristData(separated[0], items);
				}
			}
		} catch (FileNotFoundException e) {
			System.out.println("Файл " + input_filename + " не найден");
			e.printStackTrace();
		}
		return tourist_data;
	}

	// вывод результата в файл
    private void output(String output_filename, TouristData[] tourist_data) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(output_filename, false));
			for (TouristData data : tourist_data) {
				writer.append(data.getName());
				writer.append(" :");
				int[] weights = data.getItems();
				for (int weight : weights) {
					writer.append(" ");
					writer.append(Integer.toString(weight));
				}
				writer.newLine();
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
}

/*
class Broadcast extends SimpleBehaviour {
	private int step = 0;

	private final TouristData[] tourist_data;

	public Broadcast(TouristData[] tourist_data) {
		this.tourist_data = tourist_data;
	}

	// Вычисление среднего веса рюкзака
	private double calculateAverage() {
		double average = 0.0;
		for (TouristData data : this.tourist_data) {
			int[] items = data.getItems();
			for (int item : items) average += item;
		}
		average /= this.tourist_data.length;
		return average;
	}

	public void action() {
		// Отправка весов каждому туристу
		switch (this.step) {
			case 0 -> {
				System.out.println(this.myAgent.getLocalName() + " : отправка весов...");
				for (TouristData data : this.tourist_data) {
					ACLMessage weights_message = new ACLMessage(ACLMessage.INFORM);
					weights_message.setOntology("weights");
					// Преобразование массива весов в строку
					StringBuilder content = new StringBuilder();
					int[] items = data.getItems();
					for (int item : items)
						content.append(item).append(" ");
					weights_message.setContent(content.toString());
					// указание локального имени агента
					weights_message.addReceiver(new AID(data.getName(), AID.ISLOCALNAME));
					// Отправка
					this.myAgent.send(weights_message);
				}
			}
			// Отправка среднего веса рюкзака каждому туристу
			case 1 -> {
				System.out.println(this.myAgent.getLocalName() + " : отправка среднего веса рюкзака...");
				double average = this.calculateAverage();
				ACLMessage average_message = new ACLMessage(ACLMessage.INFORM);
				average_message.setOntology("average");
				average_message.setContent(Double.toString(average));
				for (TouristData data : this.tourist_data)
					average_message.addReceiver(new AID(data.getName(), AID.ISLOCALNAME));
				this.myAgent.send(average_message);
			}
		}
		this.step++;
	}

	public boolean done() {
		return this.step >= 2;
	}
}*/

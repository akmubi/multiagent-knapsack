import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.*;

public class Main {
	public static void main(String[] args) {
		Runtime runtime = Runtime.instance();
		Profile profile = new ProfileImpl();

		if (args.length == 3) {
			// [Клиенская часть]
			// Настройка создаваемого контейнера
			System.out.println("main : создание клиентского контейнера");
			profile.setParameter(Profile.CONTAINER_NAME, "ClientContainer");
			System.out.println("main : адрес главного контейнера - " + args[0]);
			profile.setParameter(Profile.MAIN_HOST, args[0]);

			// Создание контейнера
			AgentContainer container = runtime.createAgentContainer(profile);

			// Названия входного и выходного файлов
			Object[] client_args = new Object[2];
			System.out.println("main : название входного файла - " + args[1]);
			System.out.println("main : название выходного файла - " + args[2]);
			client_args[0] = args[1];
			client_args[1] = args[2];

			// Создание и запуск клиенской части
			try {
				System.out.println("main : создание и запуск агента-клиента");
				AgentController client = container.createNewAgent("client", "ClientMain", client_args);
				client.start();
			} catch (StaleProxyException e) {
				e.printStackTrace();
			}
		} else if (args.length == 1) {
			// [Серверная часть]
			// Установка названия главного контейнера
			System.out.println("main : создание главного контейнера");
			profile.setParameter(Profile.CONTAINER_NAME, "ServerContainer");

			// Создание главного контейнера
			AgentContainer container = runtime.createMainContainer(profile);

			// Названия входного файла (с именами туристов)
			Object[] server_args = new Object[1];
			System.out.println("main : название входного файла - " + args[0]);
			server_args[0] = args[0];

			// Создание и запуск серверной части
			try {
				System.out.println("main : создание и запуск агента-сервера");
				AgentController server = container.createNewAgent("server", "ServerMain", server_args);
				server.start();
			} catch (StaleProxyException e) {
				e.printStackTrace();
			}
		} else {
			System.out.println("Invalid command-line arguments");
		}
	}
}

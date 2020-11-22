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
			profile.setParameter(Profile.CONTAINER_NAME, "ClientContainer");
			profile.setParameter(Profile.MAIN_HOST, args[0]);

			// Создание контейнера
			AgentContainer container = runtime.createAgentContainer(profile);

			// Названия входного и выходного файлов
			Object[] client_args = new Object[2];
			client_args[0] = args[1];
			client_args[1] = args[2];

			// Создание и запуск клиенской части
			try {
				AgentController client = container.createNewAgent("client", "ClientMain", client_args);
				client.start();
			} catch (StaleProxyException e) {
				e.printStackTrace();
			}
		} else if (args.length == 0) {
			// [Серверная часть]
			// Установка названия главного контейнера
			profile.setParameter(Profile.CONTAINER_NAME, "ServerContainer");

			// Создание главного контейнера
			AgentContainer container = runtime.createMainContainer(profile);
			// Создание и запуск серверной части
			try {
				AgentController server = container.createNewAgent("server", "ServerMain", null);
				server.start();
			} catch (StaleProxyException e) {
				e.printStackTrace();
			}
		} else {
			System.out.println("Invalid command-line arguments");
		}
	}
}

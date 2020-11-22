import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.tools.introspector.gui.MessageTableModel;

public class ACLUtilities {
	// Получение сообщения, которое содержит только строку
	public static String blockingReceiveString(Agent agent, MessageTemplate template) {
		ACLMessage message = agent.blockingReceive(template);
		return message.getContent();
	}

	// Получение сообщения, которое содержит только целое число
	public static int blockingReceiveInteger(Agent agent, MessageTemplate template) {
		String content = blockingReceiveString(agent, template);
		return Integer.parseInt(content);
	}
}

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

class DFUtilities {
	// Нахождение всех агентов, предоставляющих сервис
	public static AID[] searchService(Agent agent, String service_type) {
		DFAgentDescription desc = new DFAgentDescription();
		ServiceDescription service_desc = new ServiceDescription();
		service_desc.setType(service_type);
		desc.addServices(service_desc);

		SearchConstraints all = new SearchConstraints();
		all.setMaxResults((long)-1);
		try {
			DFAgentDescription[] result = DFService.search(agent, desc, all);
			AID[] agents = new AID[result.length];
			for (int i = 0; i < result.length; ++i)
				agents[i] = result[i].getName();
			return agents;
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		return new AID[0];
	}

	// Нахождение первого попавшегося агента, предоставляющего сервис
	public static AID getService(Agent agent, String service_type) {
		DFAgentDescription desc = new DFAgentDescription();
		ServiceDescription service_desc = new ServiceDescription();
		service_desc.setType(service_type);
		desc.addServices(service_desc);
		try {
			DFAgentDescription[] result = DFService.search(agent, desc);
			if (result.length > 0)
				return result[0].getName();
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void deregister(Agent agent) {
		try {
			DFService.deregister(agent);
		} catch (FIPAException e) {
			e.printStackTrace();
		}
	}

	public static void register(Agent agent, String service_type) {
		DFAgentDescription desc = new DFAgentDescription();
		desc.setName(agent.getAID());
		ServiceDescription service_desc = new ServiceDescription();
		service_desc.setType(service_type);
		service_desc.setName(agent.getLocalName());
		desc.addServices(service_desc);
		try {
			DFService.register(agent, desc);
		} catch (FIPAException e) {
			System.out.println(agent.getLocalName() + " : ошибка при регистрации");
			e.printStackTrace();
		}
	}
}
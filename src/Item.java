public class Item {
	protected String name;
	protected int weight;

	public Item(String name, int weight) {
		this.name = name;
		this.weight = weight;
	}

	public String getName() {
		return this.name;
	}

	public int getWeight() {
		return this.weight;
	}

}

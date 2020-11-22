public class TouristItem extends  Item {
	private final int count;

	public TouristItem() {
		super("", 0);
		this.count = 1;
	}

	public TouristItem(String name, int weight, int count) {
		super(name, weight);
		this.count = count;
	}

	public int getCount() {
		return this.count;
	}
}

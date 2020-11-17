public class TouristData {
	private String name;
	private int[] items;

	public TouristData() {
		this.name = "";
		this.items = new int[0];
	}

	public TouristData(String name, int[] items) {
		this.name = name;
		this.items = items;
	}

	public int[] getItems() {
		return this.items;
	}

	public Object[] getItemObjects() {
		Object[] casted = new Object[this.items.length];
		for (int i = 0; i < this.items.length; ++i) {
			casted[i] = this.items[i];
		}
		return casted;
	}

	public void setItemObjects(Object[] casted) {
		int[] new_items = new int[casted.length];
		for (int i = 0; i < casted.length; ++i) {
			new_items[i] = (int)casted[i];
		}
		this.items = new_items;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String new_name) {
		this.name = new_name;
	}

	public void setItems(int[] new_items) {
		this.items = new_items;
	}

	public String toString() {
		StringBuilder output = new StringBuilder();
		// записываем имя
		output.append(this.name);
		// записываем предметы
		output.append("[");
		for (int item : this.items) {
			output.append(" ");
			output.append(item);
		}
		output.append(" ]");
		return output.toString();
	}

}

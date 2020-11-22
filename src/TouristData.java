import java.util.ArrayList;

public class TouristData {
	private String name;
	private ArrayList<TouristItem> items;

	public TouristData(String name, ArrayList<TouristItem> items) {
		this.name = name;
		this.items = items;
	}

	public String getName() {
		return this.name;
	}

	public ArrayList<TouristItem> getItems() {
		return this.items;
	}
}

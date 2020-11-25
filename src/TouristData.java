import java.util.ArrayList;

public class TouristData {
	private final String name;
	private final ArrayList<TouristItem> items;

	public TouristData(String name) {
		this.name = name;
		this.items = new ArrayList<>();
	}
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

	public TouristData getZipped() {
		return new TouristData(this.name, TouristItem.zip(this.items));
	}

	public TouristData getUpzipped() {
		return new TouristData(this.name, TouristItem.unzip(this.items));
	}

	public static String toString(TouristData data) {
		return data.name + " " + TouristItem.toString(data.items);
	}

	public static TouristData parseTouristData(String content) throws Exception {
		String[] pieces = content.split(" ");
		if (pieces.length < 1) {
			throw new Exception();
		}

		StringBuilder builder = new StringBuilder();
		for (int i = 1; i < pieces.length; ++i) {
			builder.append(pieces[i]);
			if (i != pieces.length - 1) {
				builder.append(" ");
			}
		}
		content = builder.toString();
		return new TouristData(pieces[0], TouristItem.parseTouristItems(content));
	}
}

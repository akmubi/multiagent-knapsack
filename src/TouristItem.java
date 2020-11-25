import java.util.ArrayList;

public class TouristItem extends  Item {
	private int count;

	public TouristItem(String name, int weight) {
		super(name, weight);
		this.count = 1;
	}

	public TouristItem(String name, int weight, int count) {
		super(name, weight);
		this.count = count;
	}

	public int getCount() {
		return this.count;
	}

	// разделение копий предметов
	public static ArrayList<TouristItem> unzip(ArrayList<TouristItem> items) {
		ArrayList<TouristItem> new_items = new ArrayList<>();
		for (TouristItem item : items) {
			for (int i = 0; i < item.count; ++i) {
				TouristItem new_item = new TouristItem(item.name, item.weight);
				new_items.add(new_item);
			}
		}
		return new_items;
	}

	private static int searchByName(ArrayList<TouristItem> items, String name) {
		int items_count = items.size();
		for (int i = 0; i < items_count; ++i) {
			String item_name = items.get(i).name;
			if (item_name.equals(name)) {
				return i;
			}
		}
		return -1;
	}

	static int searchByWeight(ArrayList<TouristItem> items, int weight) {
		int items_count = items.size();
		for (int i = 0; i < items_count; ++i) {
			int item_weight = items.get(i).weight;
			if (item_weight == weight) {
				return i;
			}
		}
		return -1;
	}

	// нахождение наибольшего веса, который не превышает заданный
	static int searchMaxNotExceed(ArrayList<TouristItem> items, int weight) {
		int max = -1;
		int index = -1;
		for (int i = 0; i < items.size(); ++i) {
			int item_weight = items.get(i).weight;
			if (item_weight <= weight && item_weight > max) {
				max = item_weight;
				index = i;
			}
		}
		return index;
	}

	// слияние копий предметов
	public static ArrayList<TouristItem> zip(ArrayList<TouristItem> items) {
		ArrayList<TouristItem> new_items = new ArrayList<>();
		for (TouristItem item : items) {
			int index = TouristItem.searchByName(new_items, item.name);
			if (index != -1) {
				new_items.get(index).count++;
			} else {
				new_items.add(item);
			}
		}
		return new_items;
	}

	public static double calculateAverage(ArrayList<TouristItem> items, int tourist_count) {
		return TouristItem.getSum(items) / (1.0 * tourist_count);
	}

	public static ArrayList<TouristItem> parseTouristItems(String content) throws Exception {
		String[] pieces = content.split(" ");
		if (pieces.length % 3 != 0) {
			throw new Exception();
		}
		ArrayList<TouristItem> items = new ArrayList<>();
		for (int i = 0; i < pieces.length; i += 3) {
			String name = pieces[i];
			try {
				int weight = Integer.parseInt(pieces[i + 1]);
				int count = Integer.parseInt(pieces[i + 2]);
				items.add(new TouristItem(name, weight, count));
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
		return items;
	}

	public static String toString(ArrayList<TouristItem> items) {
		int items_count = items.size();
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < items_count; ++i) {
			builder.append(items.get(i).name);
			builder.append(" ");
			builder.append(items.get(i).weight);
			builder.append(" ");
			builder.append(items.get(i).count);
			if (i != items_count - 1) {
				builder.append(" ");
			}
		}
		return builder.toString();
	}

	public static int getSum(Iterable<TouristItem> items) {
		int sum = 0;
		for (TouristItem item : items) {
			sum += item.weight * item.count;
		}
		return sum;
	}

	public static double getAverage(ArrayList<TouristItem> items) {
		return TouristItem.getSum(items) / (1.0 * items.size());
	}
}

import java.io.*;
import java.util.ArrayList;

public class DefaultIOReaderWriter implements IOReaderWriter {
	@Override
	public ArrayList<TouristItem> read(String input_filename) {
		/*
		 *   <> - название поля
		 *   [] - необязательное поле
		 *
		 *   Формат входных данных:
		 *   <название предмета> <вес предмета> [<количество>]
		 *
		 *   Пример:
		 *   Стакан 100 2
		 *   Ложка 10 3
		 *   Радио 500
		 * */
		ArrayList<TouristItem> input_items = new ArrayList<>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader(input_filename));
			String line;
			int single_item_count = 0;
			try {
				while ( (line = reader.readLine()) != null ) {
					String[] separated = line.split(" ");
					if (separated.length == 3) {
						// чтение количества текущего предмета
						try {
							single_item_count = Integer.parseInt(separated[2]);
							if (single_item_count < 1) {
								throw new Exception();
							}
						} catch (Exception e) {
							System.out.println("Ошибка при чтении количества предметов");
							e.printStackTrace();
						}
					} else if (separated.length < 2) {
						throw new IOException();
					}
						try {
							int weight = Integer.parseInt(separated[1]);
							if (weight < 1) {
								throw new Exception();
							}
							TouristItem new_item = new TouristItem(separated[0], weight, single_item_count);
							input_items.add(new_item);
						} catch (Exception e) {
							System.out.println("Ошибка при чтении веса предмета - '" + separated[0] + "'");
							e.printStackTrace();
						}
				}
				reader.close();
			} catch (IOException e) {
				System.out.println("Ошибка при чтении файла " + input_filename);
				e.printStackTrace();
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return input_items;
	}

	@Override
	public void write(ArrayList<TouristData> tourist_data, String output_filename) {
		/*
		 *   <> - название поля
		 *   [] - необязательное поле
		 *
		 *   Формат выходных данных:
		 *   <Имя туриста>:
		 *      <Название предмета> <вес предмета> [<количество>]
		 *
		 *   Пример:
		 *   Петя:
		 *      Стакан 100 2
		 *      Ложка 10 3
		 *      Медкомплект 800
		 *   Маша:
		 *      Радио 500
		 *      Гитара 1500
		 * */
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(output_filename, false));
			for (TouristData data : tourist_data) {
				writer.append(data.getName());
				writer.append(":");
				ArrayList<TouristItem> items = data.getItems();
				for (TouristItem item : items) {
					writer.append("\n\t");
					writer.append(item.getName());
					writer.append(" ");
					writer.append(Integer.toString(item.getWeight()));
					writer.append(" ");
					int item_count = item.getCount();
					if (item_count > 1) {
						writer.append(Integer.toString(item_count));
					}
				}
				writer.newLine();
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

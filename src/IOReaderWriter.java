import java.util.ArrayList;

public interface IOReaderWriter {
	ArrayList<TouristItem> read(String input_filename);

	void write(ArrayList<TouristData> tourist_data, String output_filename);
}

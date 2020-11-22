import java.util.ArrayList;

public class IOUtility {
	private String input_filename;
	private String output_filename;
	private IOReaderWriter reader_writer;

	public IOUtility(String input_filename, String output_filename) {
		this.input_filename = input_filename;
		this.output_filename = output_filename;
		this.reader_writer = new DefaultIOReaderWriter();
	}

	public void setReaderWriter(IOReaderWriter reader_writer) {
		this.reader_writer = reader_writer;
	}

	public void setInputFilename(String input_filename) {
		this.input_filename = input_filename;
	}

	public void setOutputFilename(String output_filename) {
		this.output_filename = output_filename;
	}

	public ArrayList<TouristItem> readItems() {
		return this.reader_writer.read(this.input_filename);
	}

	public void writeItems(TouristData[] tourist_data) {
		this.reader_writer.write(tourist_data, this.output_filename);
	}
}

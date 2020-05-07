package singlePlanAlgo;

import java.net.URL;
import java.util.Map;

import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

public class MaaSConfigGroup extends ReflectiveConfigGroup{
	
	public static final String GROUP_NAME = "MAAS";

	public static final String INPUT_FILE= "inputPacakgesFile";
	
	private String inputFile = null;

	public MaaSConfigGroup() {
		super(MaaSConfigGroup.GROUP_NAME);
	}
	
	@Override
	public Map<String,String> getComments() {
		final Map<String,String> comments = super.getComments();

		comments.put( INPUT_FILE , "Use the xml file format reader and writer." );

		return comments;
	}
	
	/* direct access */

	@StringGetter( INPUT_FILE )
	public String getInputFile() {
		return this.inputFile;
	}
	@StringSetter( INPUT_FILE )
	public void setInputFile(final String inputFile) {
		this.inputFile = inputFile;
	}
	
	public URL getPackagesFileURL(URL context) {
		return ConfigGroup.getInputFileURL(context, this.inputFile);
	}
}

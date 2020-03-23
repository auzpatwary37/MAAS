package optimizerAgent;

import org.matsim.core.utils.collections.Tuple;
import org.matsim.utils.objectattributes.AttributeConverter;

public class VariableDetails{

	public final String variableName;
	public final Tuple<Double,Double> limit;
	private double currentValue;
	
	
	public VariableDetails(String name,Tuple<Double,Double> limit, double intialValue) {
		this.variableName = name;
		this.limit = limit;
		this.currentValue = intialValue;
	}
	
	public double getCurrentValue() {
		return currentValue;
	}
	public void setCurrentValue(double currentValue) {
		this.currentValue = currentValue;
	}
	public String getVariableName() {
		return variableName;
	}
	public Tuple<Double, Double> getLimit() {
		return limit;
	}
	
	@Override
	public String toString() {
		return this.variableName+"___"+limit.getFirst()+"___"+limit.getSecond()+"___"+currentValue;
	}
	
	/**
	 * bind this attributeConverter as attribute converter inside the module
	 * @return
	 */
	public static AttributeConverter<VariableDetails> getAttributeConverter() {
		return new AttributeConverter<VariableDetails>() {

			@Override
			public VariableDetails convert(String value) {
				String[] part = value.split("___"); 
				return new VariableDetails(part[0], new Tuple<Double,Double>(Double.parseDouble(part[1]),Double.parseDouble(part[2])), Double.parseDouble(part[3]));
			}

			@Override
			public String convertToString(Object o) {
			VariableDetails v = (VariableDetails)o;
				
				return v.toString();
			}
			
		};
		
	}
	
	
}

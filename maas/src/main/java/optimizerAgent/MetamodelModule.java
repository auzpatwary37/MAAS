package optimizerAgent;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.matsim.core.config.Config;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.utils.collections.Tuple;


public class MetamodelModule extends AbstractModule{
	
	private Map<String,Tuple<Double,Double>> timeBeans;
	
	//Currently we will bind the planTranslator here
	
	public MetamodelModule() {
		
	}
	
	public MetamodelModule(Map<String,Tuple<Double,Double>> timeBeans) {
		this.timeBeans = timeBeans;
	}
	

	@Override
	public void install() {
		
		if (timeBeans != null) {
			bind(timeBeansWrapped.class).toInstance(new timeBeansWrapped(timeBeans));
		} else {
			bind(timeBeansWrapped.class).toProvider(TimeBeansWrappedProvider.class).in(Singleton.class);
		}
		
		//Bind any other controller listener needed
		
		this.addControlerListenerBinding().toInstance(new PlanTranslationControlerListener());
		
		//Bind Attribute Handlers
		this.addAttributeConverterBinding(VariableDetails.class).toInstance(VariableDetails.getAttributeConverter());
		this.addAttributeConverterBinding(SimpleTranslatedPlan.class).toInstance(SimpleTranslatedPlan.getAttributeConverter());
		System.out.println();
	}
	
	private static class TimeBeansWrappedProvider implements Provider<timeBeansWrapped> {
		
		@Inject Config matsimConfig;
		@Override
		public timeBeansWrapped get() {
			double startTime = matsimConfig.qsim().getStartTime();
			double endTime = matsimConfig.qsim().getEndTime();
			Map<String,Tuple<Double,Double>> timeBeans = new HashMap<>();
			int hour = ((int)startTime/3600)+1;
			for(double i = 0; i < endTime; i = i+3600) {
				timeBeans.put(Integer.toString(hour), new Tuple<>(i,i+3600));
				hour = hour + 1;
			}
			return new timeBeansWrapped(timeBeans);
		}
	}

}




class timeBeansWrapped{
	public final Map<String,Tuple<Double,Double>> timeBeans;
	
	public timeBeansWrapped(Map<String,Tuple<Double,Double>> timeBeans) {
		this.timeBeans = timeBeans;
	}
}
package optimizerAgent;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.matsim.core.config.Config;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.utils.collections.Tuple;


public class MaaSOperatorOptimizationModule extends AbstractModule{
	
	private Map<String,Tuple<Double,Double>> timeBeans;
	
	//Currently we will bind the planTranslator here
	
	public MaaSOperatorOptimizationModule() {
		
	}
	
	public MaaSOperatorOptimizationModule(Map<String,Tuple<Double,Double>> timeBeans) {
		this.timeBeans = timeBeans;
	}
	

	@Override
	public void install() {
		
		if (timeBeans != null) {
			bind(timeBeansWrapper.class).toInstance(new timeBeansWrapper(timeBeans));
		} else {
			bind(timeBeansWrapper.class).toProvider(TimeBeansWrappedProvider.class).in(Singleton.class);
		}
		
		//Bind any other controller listener needed
		
		this.addControlerListenerBinding().to(PlanTranslationControlerListener.class).asEagerSingleton();
		
		//Bind Attribute Handlers
		this.addAttributeConverterBinding(VariableDetails.class).toInstance(VariableDetails.getAttributeConverter());
		this.addAttributeConverterBinding(SimpleTranslatedPlan.class).toInstance(SimpleTranslatedPlan.getAttributeConverter());
		System.out.println();
	}
	
	private static class TimeBeansWrappedProvider implements Provider<timeBeansWrapper> {
		
		@Inject Config matsimConfig;
		@Override
		public timeBeansWrapper get() {
			double startTime = matsimConfig.qsim().getStartTime().seconds();
			double endTime = matsimConfig.qsim().getEndTime().seconds();
			Map<String,Tuple<Double,Double>> timeBeans = new HashMap<>();
			int hour = ((int)startTime/3600)+1;
			for(double i = 0; i < endTime; i = i+3600) {
				timeBeans.put(Integer.toString(hour), new Tuple<>(i,i+3600));
				hour = hour + 1;
			}
			return new timeBeansWrapper(timeBeans);
		}
	}

}





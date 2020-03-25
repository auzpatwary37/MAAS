package singlePlanAlgo;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.matsim.core.config.Config;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.utils.collections.Tuple;

import com.google.inject.name.Names;

import optimizerAgent.PlanTranslator;
import optimizerAgent.SimpleTranslatedPlan;
import optimizerAgent.VariableDetails;

public class MAASDataLoader extends AbstractModule{
	
	private final MAASPackages maasPacakges;
	private Map<String,Tuple<Double,Double>> timeBeans;

	public MAASDataLoader() {
		this.maasPacakges = null;
	}

	public MAASDataLoader(MAASPackages packages, Map<String,Tuple<Double,Double>> timeBeans) {
		this.maasPacakges = packages;
		this.timeBeans = timeBeans;
	}

	@Override
	public void install() {
		if (this.maasPacakges != null) {
			bind(MAASPackages.class).annotatedWith(Names.named("MAASPackages")).toInstance(this.maasPacakges);
		} else {
			bind(MAASPackages.class).annotatedWith(Names.named("MAASPackages")).toProvider(MAASPackagesProvider.class).in(Singleton.class);
		}
		
		
		
	}
	
	private static class MAASPackagesProvider implements Provider<MAASPackages> {
		@Inject MAASConfigGroup config;
		@Inject Config matsimConfig;
		@Override
		public MAASPackages get() {
			String path = config.getPackagesFileURL(matsimConfig.getContext()).toString();
			return new MAASPackagesReader().readPackagesFile(path);
			
		}
	}


}

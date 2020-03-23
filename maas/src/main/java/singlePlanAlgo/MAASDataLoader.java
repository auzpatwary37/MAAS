package singlePlanAlgo;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.matsim.core.config.Config;
import org.matsim.core.controler.AbstractModule;
import com.google.inject.name.Names;

public class MAASDataLoader extends AbstractModule{
	
	private final MAASPackages maasPacakges;

	public MAASDataLoader() {
		this.maasPacakges = null;
	}

	public MAASDataLoader(MAASPackages packages) {
		this.maasPacakges = packages;
	}

	@Override
	public void install() {
		if (this.maasPacakges != null) {
			bind(MAASPackages.class).annotatedWith(Names.named("MAASPackages")).toInstance(this.maasPacakges);
		} else {
			bind(MAASPackages.class).annotatedWith(Names.named("MAASPackages")).toProvider(MAASPackagesProvider.class).in(Singleton.class);
		}
		//Bind any other controller listener needed
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

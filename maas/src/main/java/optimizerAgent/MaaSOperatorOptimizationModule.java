package optimizerAgent;

import javax.inject.Singleton;

import org.matsim.core.controler.AbstractModule;

public class MaaSOperatorOptimizationModule extends AbstractModule{
	
	//Here we assume that the MaaSPacakge is already bounded 
	//MaaSOperatorAgents are also added already to the population

	@Override
	public void install() {
		this.addControlerListenerBinding().to(MaaSOperatorControlerListener.class).asEagerSingleton();
		bind(MaaSOperatorControlerListener.class).in(Singleton.class);
		
	}

}

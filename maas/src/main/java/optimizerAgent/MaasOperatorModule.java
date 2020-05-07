package optimizerAgent;

import javax.inject.Singleton;

import org.matsim.core.controler.AbstractModule;

public class MaasOperatorModule extends AbstractModule{

	@Override
	public void install() {
		// TODO Auto-generated method stub
		this.addControlerListenerBinding().to(MaaSOperatorControlerListener.class).asEagerSingleton();
		bind(MaaSOperatorControlerListener.class).in(Singleton.class);
	}

}

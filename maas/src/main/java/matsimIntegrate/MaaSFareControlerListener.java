package matsimIntegrate;

import org.apache.log4j.Logger;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationStartsListener;

import transitCalculatorsWithFare.TransitFareControlerListener;

public class MaaSFareControlerListener implements BeforeMobsimListener, IterationStartsListener{

	final static private Logger log = Logger.getLogger(TransitFareControlerListener.class);

	@Override
	/**
	 * Before the iteration starts, reset the 
	 */
	public void notifyIterationStarts(IterationStartsEvent event) {
		//FareDynamicTransitTimeAndDisutilityMaaS.resetFareSaved();
	}

	@Override
	public void notifyBeforeMobsim(BeforeMobsimEvent event) {
//		log.info("The bus fare discounted is " + FareDynamicTransitTimeAndDisutilityMaaS.getBusFareSaved()+".");
//		log.info("The train fare discounted is " + FareDynamicTransitTimeAndDisutilityMaaS.getTrainFareSaved()+".");
//		log.info("The ferry fare discounted is " + FareDynamicTransitTimeAndDisutilityMaaS.getFerryFareSaved()+".");
	}
	

}

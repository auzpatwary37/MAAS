package optimizerAgent;

import java.util.Map;

import org.matsim.core.utils.collections.Tuple;

import com.google.inject.Inject;

import dynamicTransitRouter.fareCalculators.FareCalculator;

public class timeBeansWrapper{
	@Inject
	Map<String,FareCalculator> fareCalculators;
	final Map<String,Tuple<Double,Double>>timeBeans;
	
	public timeBeansWrapper(final Map<String,Tuple<Double,Double>>timeBean) {
		this.timeBeans = timeBean;
	}
}

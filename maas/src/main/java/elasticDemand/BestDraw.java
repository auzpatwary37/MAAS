package elasticDemand;

import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;

public class BestDraw<T> implements DrawFromChoice<T> {

	@Override
	public Id<T> draw(Map<Id<T>, Double> Score) {
		double bestValue = 0;
		Id<T> bestKey = null;
		for(Entry<Id<T>,Double> e:Score.entrySet()) {
			if(bestValue<e.getValue()) {
				bestValue = e.getValue();
				bestKey = e.getKey();
			}
		}
		return bestKey;
	}

	

}

package elasticDemand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.core.gbl.MatsimRandom;

public class RandomDraw<T> implements DrawFromChoice<T> {
	
	public final Random random = MatsimRandom.getRandom();
	
	@Override
	public Id<T> draw(Map<Id<T>, Double> score) {
		List<Id<T>> keysAsArray = new ArrayList<Id<T>>(score.keySet());
		return keysAsArray.get(random.nextInt(keysAsArray.size()));
	}
	
	public T draw(Set<T> elements) {
		return new ArrayList<T>(elements).get(random.nextInt(elements.size()));
	}

}

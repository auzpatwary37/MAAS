package elasticDemand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.matsim.api.core.v01.Id;
import org.matsim.core.gbl.MatsimRandom;

import com.google.common.primitives.Doubles;

public class LogitDraw<T> implements DrawFromChoice<T>{
	
	public final Random random = MatsimRandom.getRandom();


	@Override
	public Id<T> draw(Map<Id<T>, Double> Score) {
		List<Id<T>> ids = new ArrayList<>();
		double[] logit = new double[Score.size()];
		int i = 0;
		double cumulLogit = 0;
		double max = Double.NEGATIVE_INFINITY;
		for(double d:Score.values())if(max<d)max=d;
	
		for(Entry<Id<T>, Double> e:Score.entrySet()) {
			ids.add(e.getKey());
			logit[i] = Math.exp(e.getValue()-max);
			cumulLogit += logit[i];
			i++;
		}
		double upToSum = 0;
		double randNum = random.nextDouble();
		for(i=0;i<logit.length;i++) {
			logit[i] = upToSum+logit[i]/cumulLogit;
			upToSum=logit[i];
			if(logit[i]>randNum) {
				return ids.get(i);
			}
		}
		return null;
	}

}

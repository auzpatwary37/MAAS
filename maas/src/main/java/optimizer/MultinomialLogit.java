package optimizer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

public class MultinomialLogit {
static Random generator = new Random();
private Map<String,Double> distribution;
private Map<String,Double> probabilities;
private int range;
private String lastKey = null;

//Constructor
public MultinomialLogit(Map<String,Double> utilities){
	
	Map<String,Double> probabilities = new HashMap<>(utilities);
	double maxUtil = Collections.max(utilities.values());
	
	double sum = 0;
	for(Entry<String, Double> d:utilities.entrySet()) {
		double dd = Math.exp(d.getValue()-maxUtil);
		sum+=dd;
		probabilities.put(d.getKey(),dd);
	}
	
	for(Entry<String, Double> d:probabilities.entrySet()) {
		probabilities.put(d.getKey(),d.getValue()/sum);
	}
	this.probabilities = probabilities;
	range = probabilities.size();
	distribution = new HashMap<>();
	double position = 0;
	int i = 0;
	for (Entry<String, Double> d:probabilities.entrySet()){
		position += d.getValue();
		distribution.put(d.getKey(), position);
		if(i==range-1)lastKey = d.getKey();
	}
	 
}

public String sample() {
	double uniform = generator.nextDouble();
	for (Entry<String, Double> d:this.distribution.entrySet()){
		if (uniform < d.getValue()){
			return d.getKey();
		}
	}
	return lastKey;
}

public Map<String, Double> getProbabilities() {
	return probabilities;
}



}
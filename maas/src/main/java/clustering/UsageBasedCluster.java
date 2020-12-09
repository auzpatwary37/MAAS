package clustering;

import java.util.HashMap;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.matsim.api.core.v01.Id;

public class UsageBasedCluster <T>{
	
	public Set<Set<Id<T>>> createUsageBasedSplit(Map<Id<T>,T> inSet,Map<Id<T>,Double> weight ,int splitSize){
		List<Set<Id<T>>> outSets = new ArrayList<>();
		Map<Id<T>, Double> sortedByCount = weight.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		int i= 0;
		Set<Id<T>> set=null;
		for(Id<T>k:sortedByCount.keySet()) {
			if(i == 0) {
				set = new HashSet<>();
			}
			if(i<(float)weight.size()/splitSize) {
				set.add(k);
			}else {
				outSets.add(set);
				i = -1;
			}
			i++;
		}
		for(Id<T> k:inSet.keySet()) {
			if(!sortedByCount.containsKey(k))outSets.get(0).add(k);
		}
		
		return new HashSet<>(outSets);
	}
	
	


			
}

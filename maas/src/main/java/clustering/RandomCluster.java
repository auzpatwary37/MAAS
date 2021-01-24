package clustering;

import java.util.*;

public class RandomCluster <T> {
	
	public Set<Set<T>> createRandomSplit(Set<T> set, int splitSize){
		Map<Integer,Set<T>> outSets = new HashMap<>();
		Random rnd = new Random();
		for(int i =0;i<splitSize;i++)outSets.put(i, new HashSet<>());
		for(T a:set)outSets.get(rnd.nextInt(splitSize)).add(a);
		return new HashSet<>(outSets.values());
	}
	
}

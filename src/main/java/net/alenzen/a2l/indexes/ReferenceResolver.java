package net.alenzen.a2l.indexes;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Stack;

import net.alenzen.a2l.Asap2File;
import net.alenzen.a2l.IAsap2TreeElement;

public class ReferenceResolver {
	private Asap2File file;
	private Stack<ReferenceResolverStackEntry> stack;
	private HashSet<IAsap2TreeElement> visitedNodes;

	/**
	 * Idea - go down the tree and save the stack (do not visit nodes already
	 * visited; use a Set to mark visited node) - throw an Exception if a node would
	 * be visited twice - do not go down on ReferenceResolve annotated fields - for
	 * each field with CreateIndex annotation generate an index on that stack - for
	 * each field with ReferenceResolve find the index on the stack and resolve the
	 * reference It is important to first generate all indexes of the level before
	 * trying to resolve
	 * 
	 * so each stack level contains - sub nodes left to visit - object of the level
	 * - a list of indexes Map<String, Map<String, Object>> - index name -
	 * HashMap<String, Object>
	 * 
	 * finding index on the stack - find the index on the current level - find the
	 * index on any level above -> InvalidIndexNameException
	 * 
	 * resolve the reference - get the object from the index - try to cast ->
	 * InvalidTypeException
	 * 
	 * 
	 */

	public ReferenceResolver(Asap2File file) {
		this.file = file;
	}

	public void updateReferences() {
		this.stack = new Stack<ReferenceResolverStackEntry>();
		this.visitedNodes = new HashSet<IAsap2TreeElement>();
		try {
			updateReferencesRescursive(this.file);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			// TODO create a unified exception
			e.printStackTrace();
		}
	}

	private void updateReferencesRescursive(IAsap2TreeElement currentNode)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		if (!visitedNodes.add(currentNode)) {
			// TODO throw Exception because there has been a loop detected in the structure
			return;
		}

		ReferenceResolverStackEntry currentStackEntry = new ReferenceResolverStackEntry();
		currentStackEntry.setNode(currentNode);
		this.stack.push(currentStackEntry);

		currentStackEntry.generateIndexes();
		resolveReferencesOnCurrentLevel();

		List<IAsap2TreeElement> nodes = currentNode.collectSubNodes();
		if (nodes != null) {
			for (IAsap2TreeElement n : nodes) {
				updateReferencesRescursive(n);
			}
		}

		this.stack.pop();
	}

	private void resolveReferencesOnCurrentLevel()
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		ReferenceResolverStackEntry currentLevel = this.stack.peek();
		Map<String, Map<String, Object>> indexes = collectCurrentIndexes();
		resolveReferencesOnFields(currentLevel.getNode(), indexes);
	}

	private void resolveReferencesOnFields(IAsap2TreeElement node, Map<String, Map<String, Object>> indexes)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		Class<?> currentClass = node.getClass();
		Field[] fields = currentClass.getDeclaredFields();
		while (true) {
			for (Field f : fields) {
				ReferenceResolve annotation = f.getAnnotation(ReferenceResolve.class);
				if (annotation != null) {
					String referenceString = getReferenceString(node, annotation.ref());
					Object reference = indexes.get(annotation.index()).get(referenceString);

					if (reference == null) {
						throw new NoSuchElementException(String.format("Cannot find reference '%s' in index '%s'",
								referenceString, annotation.index()));
					}

					f.setAccessible(true);
					f.set(node, reference);
				}
			}

			// loop exit condition
			currentClass = currentClass.getSuperclass();
			if (currentClass == null)
				break;

			fields = currentClass.getDeclaredFields();
		}
	}

	private String getReferenceString(IAsap2TreeElement node, String ref)
			throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		Field field = node.getClass().getDeclaredField(ref);
		field.setAccessible(true);
		// TODO throw a proper Exception if ref is not a String type
		return (String) field.get(node);
	}

	private Map<String, Map<String, Object>> collectCurrentIndexes() {
		Map<String, Map<String, Object>> indexes = new HashMap<String, Map<String, Object>>();
		this.stack.stream().forEachOrdered(e -> indexes.putAll(e.getIndexes()));
		return indexes;
	}
}

package rs117.hd.dev.editor;

import com.google.gson.JsonElement;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

final class WorkspaceHistory {
	static final class Step {
		final String label;
		final JsonElement state;
		final long millis;
		final LocalTime time = LocalTime.now();

		Step(String label, JsonElement state) {
			this.label = label;
			this.state = state;
			this.millis = System.currentTimeMillis();
		}
	}

	private final List<Step> steps = new ArrayList<>();
	private int index;

	void reset(String label, JsonElement state) {
		steps.clear();
		steps.add(new Step(label, state));
		index = 0;
	}

	void add(String label, JsonElement before, JsonElement after) {
		if (before.equals(after))
			return;
		if (steps.isEmpty())
			reset("Opened editor", before);
		boolean atEnd = index == steps.size() - 1;
		steps.subList(index + 1, steps.size()).clear();
		Step next = new Step(label, after);
		Step previous = steps.get(index);
		if (atEnd && index > 0 && label.equals(previous.label) && next.millis - previous.millis < 700) {
			steps.set(index, next);
		} else {
			steps.add(next);
			index++;
			if (steps.size() > 101) {
				steps.remove(0);
				index--;
			}
		}
	}

	int index() { return index; }
	int size() { return steps.size(); }
	Step get(int position) { return steps.get(position); }
	boolean canUndo() { return index > 0; }
	boolean canRedo() { return index + 1 < steps.size(); }
	void moveTo(int position) { index = position; }
}

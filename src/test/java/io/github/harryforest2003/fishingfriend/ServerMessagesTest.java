package io.github.harryforest2003.fishingfriend;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerMessagesTest {
	private static final String MCMMO_FISHED_OUT =
		"§7That area is suffering from overfishing. Cast your rod in a different spot for more fish. At least 3 blocks away.";

	@Test
	void matchesPhrasesIgnoringCaseAndColourCodes() {
		assertTrue(ServerMessages.containsAny(MCMMO_FISHED_OUT, List.of("OVERFISHING")));
		assertTrue(ServerMessages.containsAny("§eYou sense that there might not be many fish left in this area.",
			List.of("many fish left")));
		assertFalse(ServerMessages.containsAny("Welcome to the server!", List.of("overfishing", "overfished")));
	}

	@Test
	void ignoresBlankPhrases() {
		assertFalse(ServerMessages.containsAny("anything", List.of("", "  ")));
	}

	@Test
	void readsTheDistanceFromTheMessage() {
		assertEquals(3, ServerMessages.blocksMentioned(MCMMO_FISHED_OUT));
		assertEquals(1, ServerMessages.blocksMentioned("move 1 block over"));
		assertEquals(0, ServerMessages.blocksMentioned("This spot is overfished."));
	}
}

package com.specskart.whatsapp;

public enum BotIntent {
    // Frames flow: kept working end-to-end, just not linked from the welcome message while
    // the client's lens-only funnel is the one shown (see WhatsAppBotService.sendWelcome).
    FIND_FRAMES, EXPLORE_FRAMES, VISIT_WEBSITE,
    RESULTS_SHOW_FRAMES, RESULTS_NOT_NOW,
    HELP_CHOOSE, BUDGET_LOW, BUDGET_MED, BUDGET_HIGH,
    EXPLORE_LENS,
    GREETING, UNKNOWN
}

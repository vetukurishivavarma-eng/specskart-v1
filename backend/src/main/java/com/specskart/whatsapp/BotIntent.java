package com.specskart.whatsapp;

public enum BotIntent {
    // Frames flow: kept working end-to-end, just not linked from the welcome message while
    // the client's lens-only funnel is the one shown (see WhatsAppBotService.sendWelcome).
    FIND_FRAMES, EXPLORE_FRAMES, VISIT_WEBSITE,
    RESULTS_SHOW_FRAMES, RESULTS_NOT_NOW,
    HELP_CHOOSE, BUDGET_LOW, BUDGET_MED, BUDGET_HIGH,
    EXPLORE_LENS,
    TRACK_ORDER,
    MENU,
    CARE,
    FAQ,
    // The FB ad's prefilled "can I have more information" — lens link, then the menu.
    MORE_INFO,
    GREETING, UNKNOWN
}

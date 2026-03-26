package com.project.safetyFence.user;

import java.security.SecureRandom;

public class UserUtils {

    private static final String CHAR_POOL = "0123456789";
    private static final int LINK_CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateLinkCode() {
        StringBuilder linkCode = new StringBuilder(LINK_CODE_LENGTH);
        for (int i = 0; i < LINK_CODE_LENGTH; i++) {
            int index = RANDOM.nextInt(CHAR_POOL.length());
            linkCode.append(CHAR_POOL.charAt(index));
        }
        return linkCode.toString();
    }


}

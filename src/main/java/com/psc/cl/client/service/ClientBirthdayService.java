package com.psc.cl.client.service;

import com.psc.cl.client.dto.ClientBirthdayResponse;

import java.time.Month;
import java.util.List;

/**
 * Looks up clients' birthdays.
 */
public interface ClientBirthdayService {

    /**
     * Every client whose birthday falls in a month, whatever the year they were born. Clients without
     * a birthday recorded are left out.
     *
     * @param month the month of the year
     * @return the clients, ordered by day of the month, then first and last name; empty when there are
     *         none
     */
    List<ClientBirthdayResponse> listBirthdays(Month month);
}

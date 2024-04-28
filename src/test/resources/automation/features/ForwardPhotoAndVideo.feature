Feature: Forward photo and video
  Scenario: Forward photo and video
    Given User inserts VK group details into DB
    When User sends HTTP request to forward endpoint
    Then Expected 2 messages are present in TG channel
      | messageQuantity | messageType     |
      | 1               | PHOTO           |
      | 1               | VIDEO           |
    And VK group timestamps have been updated


update venue_elements
set background_color = '#DBEAFE', border_color = '#93C5FD'
where element_type = 'STAGE'
  and lower(background_color) = '#25324a';

update meeting_elements
set background_color = '#DBEAFE', border_color = '#93C5FD'
where element_type = 'STAGE'
  and lower(background_color) = '#25324a';

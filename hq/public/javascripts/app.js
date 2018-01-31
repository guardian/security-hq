jQuery(function($) {
  // idempotent redirect to HTTPS
  if (!/^https/.test(window.location.protocol)) {
    // eslint-disable-next-line no-console
    console.log('insecure');
    // window.location.replace(location.href.replace(/^http:/, "https:"))
  }
});

$(document).ready(function() {
  // initalizing the mobile nav and the modal
  $('.button-collapse').sideNav();
  $('.modal').modal();

  // Extra interactions for the dropdowns on the Security Groups page
  $('.js-sg-details').hover(
    function() {
      $(this).collapsible('open', 0);
    },
    function() {
      $(this).collapsible('close', 0);
    }
  );

  $('.js-sg-filter').change(function() {
    $('#show-ignored-sgs')[0].checked
      ? $('.sg-suppressed--true').show()
      : $('.sg-suppressed--true').hide();
    $('#show-flagged-sgs')[0].checked
      ? $('.sg-suppressed--false').show()
      : $('.sg-suppressed--false').hide();
  });

  $('.js-sg-details').click(function() {
    $(this).collapsible('destroy');

    var clicks = $(this).data('clicks') || false;
    if (clicks) {
      $(this).collapsible('close', 0);
    } else {
      $(this).off('mouseenter mouseleave');
      $(this).collapsible('open', 0);
    }
    $(this).data('clicks', !clicks);
  });

  // Functionality for the floating menus on the Security Groups page
  $('.js-sg-pin-close').click(function() {
    $('html, body')
      .stop()
      .animate(
        {
          scrollTop: 0
        },
        'slow'
      );
    $('.collapsible-header').removeClass(function() {
      return 'active';
    });
    $('.collapsible').collapsible({ accordion: true });
    $('.collapsible').collapsible({ accordion: false });
  });

  $('.js-sg-pin-top').click(function() {
    const scrollTarget = $(this).closest('.js-sg-scroll');
    $('html, body')
      .stop()
      .animate(
        {
          scrollTop: scrollTarget.offset().top - 10
        },
        'slow'
      );
  });

  $('.js-sg-pin-end').click(function() {
    const scrollTarget = $(this).closest('.js-sg-scroll');
    $('html, body')
      .stop()
      .animate(
        {
          scrollTop: scrollTarget[0].scrollHeight - 200
        },
        'slow'
      );
  });
});

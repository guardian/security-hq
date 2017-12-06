jQuery(function($) {
  // idempotent redirect to HTTPS
  if (!/^https/.test(window.location.protocol)) {
    console.log('insecure');
    // window.location.replace(location.href.replace(/^http:/, "https:"))
  }
});

// extensions to the security groups page
$(document).ready(function() {
  $('.js-sg-details').hover(
    function() {
      $(this)
        .find('.collapsible-header')
        .click();
    },
    function() {
      $(this)
        .find('.collapsible-header')
        .click();
    }
  );

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

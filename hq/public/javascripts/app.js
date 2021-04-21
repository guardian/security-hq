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
  // Make any tables with the filter class filterable
  $('.filterable-table').filterTable();

  $('.js-iam-expand').click(function() {
    $('.collapsible-header').addClass('active');
    $('.collapsible-body').css('display', 'block');
  });

  $('.js-iam-collapse').click(function() {
    $('.collapsible-header').removeClass(function() {
      return 'active';
    });
    $('.collapsible-body').css('display', 'none');
  });

  // Extra interactions for the dropdowns on the Security Groups page
  $('.js-finding-details').hover(
    function() {
      $(this).collapsible('open', 0);
    },
    function() {
      $(this).collapsible('close', 0);
    }
  );

  // filtering table results
  $('.js-finding-filter').change(function() {
    $('#show-ignored-findings')[0].checked
      ? $('.finding-suppressed--true').show()
      : $('.finding-suppressed--true').hide();
    $('#show-flagged-findings')[0].checked
      ? $('.finding-suppressed--false').show()
      : $('.finding-suppressed--false').hide();
  });
  $('.js-finding-filter-for-s3').change(function() {
    $('#show-unencrypted-findings')[0].checked
      ? $('.finding-unencrypted').show()
      : $('.finding-unencrypted').hide();
  });
  $('.js-finding-filter-for-gcp').change(function() {
    $('#show-critical-findings')[0].checked
        ? $('.finding-critical').show()
        : $('.finding-critical').hide();
    $('#show-high-findings')[0].checked
        ? $('.finding-high').show()
        : $('.finding-high').hide();
    $('#show-medium-findings')[0].checked
        ? $('.finding-medium').show()
        : $('.finding-medium').hide();
    $('#show-low-findings')[0].checked
        ? $('.finding-low').show()
        : $('.finding-low').hide();
    $('#show-unknown-findings')[0].checked
        ? $('.finding-unknown').show()
        : $('.finding-unknown').hide();
  });

  $('.js-finding-details').click(function() {
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
  $('.js-finding-pin-close').click(function() {
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

  $('.js-finding-pin-top').click(function() {
    const scrollTarget = $(this).closest('.js-finding-scroll');
    $('html, body')
      .stop()
      .animate(
        {
          scrollTop: scrollTarget.offset().top - 10
        },
        'slow'
      );
  });

  $('.js-finding-pin-end').click(function() {
    const scrollTarget = $(this).closest('.js-finding-scroll');
    $('html, body')
      .stop()
      .animate(
        {
          scrollTop: scrollTarget[0].scrollHeight - 200
        },
        'slow'
      );
  });
  $('.tooltipped').tooltip({enterDelay: 0, inDuration: 100});

  // Functionality for the GCP table
  $('.js-read-more').click(function() {
    $(this).parent().find('.gcp-toggle').toggle();
  });
});

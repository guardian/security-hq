jQuery(function ($) {
  // idempotent redirect to HTTPS
  if (!/^https/.test(window.location.protocol)) {
    // eslint-disable-next-line no-console
    console.log('insecure');
    // window.location.replace(location.href.replace(/^http:/, "https:"))
  }
});

$(document).ready(function () {
  // initalizing the mobile nav and the modal
  $('.button-collapse').sideNav();
  $('.modal').modal();
  // Make any tables with the filter class filterable
  $('.filterable-table').filterTable();

  $('.js-iam-expand').click(function () {
    $('.collapsible-header').addClass('active');
    $('.collapsible-body').css('display', 'block');
  });

  $('.js-iam-collapse').click(function () {
    $('.collapsible-header').removeClass(function () {
      return 'active';
    });
    $('.collapsible-body').css('display', 'none');
  });

  // Extra interactions for the dropdowns on the Security Groups page
  $('.js-finding-details').hover(
    function () {
      $(this).collapsible('open', 0);
    },
    function () {
      $(this).collapsible('close', 0);
    }
  );

  // filtering table results
  const form = document.querySelector('form.finding-filter');
  if (form) {
    const ignoredFindings = $('.finding-suppressed--true');
    const flaggedFindings = $('.finding-suppressed--false');
    const unencryptedFindings = $('.finding-unencrypted');
    const criticalFindings = $('.finding-critical');
    const highFindings = $('.finding-high');
    const mediumFindings = $('.finding-medium');
    const lowFindings = $('.finding-low');
    const unknownFindings = $('.finding-unknown');

    form.addEventListener('input', function () {
      const formData = new FormData(form);

      const {
        // S3 and Security Group filters
        showFlaggedFindings,
        showIgnoredFindings,

        // S3 filters
        showUnencryptedFindings,

        // GCP filters
        showCriticalFindings,
        showHighFindings,
        showMediumFindings,
        showLowFindings,
        showUnknownFindings,
      } = Object.fromEntries(formData);

      showIgnoredFindings ? ignoredFindings.show() : ignoredFindings.hide();
      showFlaggedFindings ? flaggedFindings.show() : flaggedFindings.hide();
      showUnencryptedFindings
        ? unencryptedFindings.show()
        : unencryptedFindings.hide();
      showCriticalFindings ? criticalFindings.show() : criticalFindings.hide();
      showHighFindings ? highFindings.show() : highFindings.hide();
      showMediumFindings ? mediumFindings.show() : mediumFindings.hide();
      showLowFindings ? lowFindings.show() : lowFindings.hide();
      showUnknownFindings ? unknownFindings.show() : unknownFindings.hide();
    });
  }

  $('.js-finding-details').click(function () {
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
  $('.js-finding-pin-close').click(function () {
    $('html, body').stop().animate(
      {
        scrollTop: 0,
      },
      'slow'
    );
    $('.collapsible-header').removeClass(function () {
      return 'active';
    });
    $('.collapsible').collapsible({ accordion: true });
    $('.collapsible').collapsible({ accordion: false });
  });

  $('.js-finding-pin-top').click(function () {
    const scrollTarget = $(this).closest('.js-finding-scroll');
    $('html, body')
      .stop()
      .animate(
        {
          scrollTop: scrollTarget.offset().top - 10,
        },
        'slow'
      );
  });

  $('.js-finding-pin-end').click(function () {
    const scrollTarget = $(this).closest('.js-finding-scroll');
    $('html, body')
      .stop()
      .animate(
        {
          scrollTop: scrollTarget[0].scrollHeight - 200,
        },
        'slow'
      );
  });
  $('.tooltipped').tooltip({ enterDelay: 0, inDuration: 100 });

  // Functionality for the GCP table
  $('.js-read-more').click(function () {
    $(this).parent().find('.gcp-toggle').toggle();
  });
});

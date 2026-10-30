(function () {
  'use strict';

  var REDUCED_MOTION = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  document.addEventListener('DOMContentLoaded', function () {
    initStarfield();
    initNavToggle();
    initLightbox();
    initCopyButton();
    initFooterYear();
    initRevealOnScroll();
    initActiveSection();
  });

  /* ---------- Footer year ---------- */
  function initFooterYear() {
    var el = document.getElementById('footerYear');
    if (el) {
      el.textContent = '© ' + new Date().getFullYear();
    }
  }

  /* ---------- Mobile nav toggle ---------- */
  function initNavToggle() {
    var toggle = document.getElementById('navToggle');
    var menu = document.getElementById('primaryMenu');
    if (!toggle || !menu) return;

    toggle.addEventListener('click', function () {
      var open = menu.classList.toggle('is-open');
      toggle.setAttribute('aria-expanded', String(open));
    });

    menu.addEventListener('click', function (e) {
      if (e.target.tagName === 'A') {
        menu.classList.remove('is-open');
        toggle.setAttribute('aria-expanded', 'false');
      }
    });
  }

  /* ---------- Reveal on scroll ---------- */
  function initRevealOnScroll() {
    var items = document.querySelectorAll('.reveal');
    if (items.length === 0) return;

    if (!('IntersectionObserver' in window) || REDUCED_MOTION) {
      for (var i = 0; i < items.length; i++) {
        items[i].classList.add('is-visible');
      }
      return;
    }

    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12 });

    items.forEach(function (item) {
      observer.observe(item);
    });
  }

  /* ---------- Active section nav highlight ---------- */
  function initActiveSection() {
    var links = document.querySelectorAll('.primary-menu a[href^="#"]');
    if (links.length === 0) return;

    var sections = [];
    links.forEach(function (link) {
      var id = link.getAttribute('href').slice(1);
      var sec = document.getElementById(id);
      if (sec) sections.push({ id: id, el: sec, link: link });
    });

    if (!('IntersectionObserver' in window)) return;

    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        links.forEach(function (l) { l.removeAttribute('aria-current'); });
        for (var i = 0; i < sections.length; i++) {
          if (sections[i].id === entry.target.id) {
            sections[i].link.setAttribute('aria-current', 'true');
          }
        }
      });
    }, { rootMargin: '-48% 0px -48% 0px' });

    sections.forEach(function (s) { observer.observe(s.el); });
  }

  /* ---------- Copy button ---------- */
  function initCopyButton() {
    var btn = document.getElementById('copyBtn');
    var code = document.getElementById('quickStartCode');
    if (!btn || !code) return;

    btn.addEventListener('click', function () {
      var text = code.textContent;
      var done = false;

      if (navigator.clipboard && navigator.clipboard.writeText) {
        try {
          navigator.clipboard.writeText(text);
          done = true;
        } catch (err) {
          done = false;
        }
      }

      if (!done) {
        fallbackSelect(code);
        done = true;
      }

      if (done) {
        var original = btn.textContent;
        btn.textContent = 'Copied!';
        btn.classList.add('copied');
        setTimeout(function () {
          btn.textContent = original;
          btn.classList.remove('copied');
        }, 2000);
      }
    });
  }

  function fallbackSelect(el) {
    try {
      var range = document.createRange();
      range.selectNodeContents(el);
      var sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(range);
    } catch (err) {
      /* selection unavailable */
    }
  }

  /* ---------- Lightbox ---------- */
  function initLightbox() {
    var gallery = document.getElementById('gallery');
    if (!gallery) return;

    var thumbs = Array.prototype.slice.call(gallery.querySelectorAll('.thumb img'));
    if (thumbs.length === 0) return;

    var dialog = document.getElementById('lightbox');
    var imgEl = document.getElementById('lightboxImg');
    var captionEl = document.getElementById('lightboxCaption');
    var closeBtn = document.getElementById('lightboxClose');
    var backdrop = document.getElementById('lightboxBackdrop');
    var prevBtn = document.getElementById('lightboxPrev');
    var nextBtn = document.getElementById('lightboxNext');

    if (!dialog || !imgEl) return;

    var currentIndex = 0;
    var lastFocused = null;

    function buildData() {
      return thumbs.map(function (t) {
        return { src: t.getAttribute('src'), alt: t.getAttribute('alt') };
      });
    }
    var data = buildData();

    function show(index) {
      if (index < 0) index = data.length - 1;
      if (index >= data.length) index = 0;
      currentIndex = index;
      imgEl.setAttribute('src', data[currentIndex].src);
      imgEl.setAttribute('alt', data[currentIndex].alt);
      captionEl.textContent = data[currentIndex].alt;
    }

    function open(index) {
      lastFocused = thumbs[index] || thumbs[0];
      show(index);
      if (dialog.showModal) {
        dialog.showModal();
      } else {
        dialog.style.display = 'flex';
      }
      document.body.style.overflow = 'hidden';
      if (closeBtn) closeBtn.focus();
    }

    function close() {
      if (dialog.close && dialog.open) {
        dialog.close();
      } else {
        dialog.style.display = 'none';
      }
      document.body.style.overflow = '';
      if (lastFocused) lastFocused.focus();
    }

    thumbs.forEach(function (thumb, i) {
      thumb.parentElement.addEventListener('click', function () {
        open(i);
      });
    });

    if (closeBtn) closeBtn.addEventListener('click', close);
    if (backdrop) backdrop.addEventListener('click', close);
    if (prevBtn) prevBtn.addEventListener('click', function () { show(currentIndex - 1); });
    if (nextBtn) nextBtn.addEventListener('click', function () { show(currentIndex + 1); });

    document.addEventListener('keydown', function (e) {
      if (!dialog.open) return;
      if (e.key === 'Escape') {
        close();
      } else if (e.key === 'ArrowLeft') {
        show(currentIndex - 1);
      } else if (e.key === 'ArrowRight') {
        show(currentIndex + 1);
      }
    });
  }

  /* ---------- Starfield ---------- */
  function initStarfield() {
    var canvas = document.getElementById('starfield');
    if (!canvas) return;
    var ctx = canvas.getContext('2d');
    if (!ctx) return;

    var COUNT = REDUCED_MOTION ? 1 : 150;
    var stars = [];
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    var width = 0;
    var height = 0;
    var running = true;
    var rafId = null;

    function resize() {
      width = window.innerWidth;
      height = window.innerHeight;
      canvas.width = Math.floor(width * dpr);
      canvas.height = Math.floor(height * dpr);
      canvas.style.width = width + 'px';
      canvas.style.height = height + 'px';
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    function makeStars() {
      stars = [];
      var tints = [
        [255, 160, 51],
        [255, 255, 255],
        [63, 169, 245]
      ];
      for (var i = 0; i < COUNT; i++) {
        var tint = tints[(Math.random() * tints.length) | 0];
        stars.push({
          x: Math.random() * width,
          y: Math.random() * height,
          r: 0.4 + Math.random() * 1.4,
          vx: (Math.random() - 0.5) * 0.12,
          vy: (Math.random() - 0.5) * 0.12,
          a: 0.3 + Math.random() * 0.7,
          tint: tint
        });
      }
    }

    function draw() {
      ctx.clearRect(0, 0, width, height);
      for (var i = 0; i < stars.length; i++) {
        var s = stars[i];
        s.x += s.vx;
        s.y += s.vy;
        if (s.x < 0) s.x += width;
        else if (s.x > width) s.x -= width;
        if (s.y < 0) s.y += height;
        else if (s.y > height) s.y -= height;

        ctx.beginPath();
        ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(' + s.tint[0] + ',' + s.tint[1] + ',' + s.tint[2] + ',' + s.a + ')';
        ctx.fill();
      }
    }

    function loop() {
      if (!running) return;
      draw();
      rafId = requestAnimationFrame(loop);
    }

    resize();
    makeStars();
    if (REDUCED_MOTION) {
      draw();
    } else {
      loop();
    }

    window.addEventListener('resize', function () {
      resize();
      for (var i = 0; i < stars.length; i++) {
        stars[i].x = Math.random() * width;
        stars[i].y = Math.random() * height;
      }
    });

    document.addEventListener('visibilitychange', function () {
      if (document.hidden) {
        running = false;
        if (rafId) cancelAnimationFrame(rafId);
      } else if (!REDUCED_MOTION) {
        running = true;
        loop();
      }
    });
  }
})();

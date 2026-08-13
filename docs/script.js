const lightbox = document.querySelector('.lightbox');
const lightboxImage = lightbox.querySelector('img');

document.querySelectorAll('.screen-card').forEach((card) => {
  card.addEventListener('click', () => {
    lightboxImage.src = card.dataset.image;
    lightboxImage.alt = card.dataset.alt;
    lightbox.showModal();
  });
});

document.querySelector('.lightbox-close').addEventListener('click', () => lightbox.close());
lightbox.addEventListener('click', (event) => {
  if (event.target === lightbox) lightbox.close();
});

document.querySelector('#year').textContent = new Date().getFullYear();

const cursorDot = document.querySelector('.cursor-dot');
window.addEventListener('pointermove', (event) => {
  cursorDot.classList.add('active');
  cursorDot.style.transform = `translate(${event.clientX + 11}px, ${event.clientY + 11}px)`;
});

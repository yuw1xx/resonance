const base = import.meta.env.BASE_URL

const LINKS = [
  { label: 'GitHub', href: 'https://github.com/yuw1xx/resonance-app' },
  { label: 'Releases', href: 'https://github.com/yuw1xx/resonance-app/releases' },
  { label: 'Report a Bug', href: 'https://github.com/yuw1xx/resonance-app/issues' },
  { label: 'MIT License', href: 'https://github.com/yuw1xx/resonance-app/blob/main/LICENSE' },
  { label: 'Terms of Service', href: `${base}terms.html` },
  { label: 'Privacy Policy', href: `${base}privacy.html` },
]

export default function Footer() {
  return (
    <footer className="py-14 px-6 border-t border-white/[0.06]">
      <div className="max-w-6xl mx-auto flex flex-col items-center text-center gap-5">
        <div className="flex items-center gap-3">
          <img src={`${base}icon.png`} alt="Resonance" className="w-8 h-8 rounded-xl" />
          <span className="font-semibold text-[#EDE9F6]">Resonance</span>
        </div>
        <p className="text-sm text-[#6B6380]">A modern local music player for Android 15+.</p>
        <nav className="flex flex-wrap gap-6 justify-center">
          {LINKS.map(link => (
            <a
              key={link.label}
              href={link.href}
              target="_blank"
              rel="noopener noreferrer"
              className="text-sm text-[#6B6380] hover:text-primary transition-colors duration-200"
            >
              {link.label}
            </a>
          ))}
        </nav>
        <p className="text-xs text-[#332D45] mt-1">
          © 2026 yuw1xx · MIT License · Inspired by Namida, Pixel Player &amp; Gramophone
        </p>
      </div>
    </footer>
  )
}

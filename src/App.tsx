import Nav from './components/Nav'
import Hero from './components/Hero'
import Screenshots from './components/Screenshots'
import Features from './components/Features'
import OpenSource from './components/OpenSource'
import Footer from './components/Footer'

export default function App() {
  return (
    <div className="min-h-screen bg-bg text-[#EDE9F6] font-sans overflow-x-hidden">
      <Nav />
      <Hero />
      <Screenshots />
      <Features />
      <OpenSource />
      <Footer />
    </div>
  )
}

import axios from 'axios'

const fallbackNetwork = axios.create({
  withCredentials: true,
  timeout: 20_000,
})

const fallbackAurora = {
  service: {
    network: fallbackNetwork,
  },
}

export default globalThis.Aurora || fallbackAurora

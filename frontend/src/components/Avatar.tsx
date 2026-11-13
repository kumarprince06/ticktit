import { useState } from "react";

/**
 * Profile picture (e.g. from Google), or the first letter of the name when there is none or it fails to load.
 * no-referrer: Google's image server can refuse requests that carry another site's address.
 */
export function Avatar({ name, url, size = 38 }: { name: string; url: string | null; size?: number }) {
  const [broken, setBroken] = useState(false);
  const style = { width: size, height: size, fontSize: size * 0.42 };
  if (url && !broken) {
    return <img className="avatar-img" src={url} alt="" style={style} referrerPolicy="no-referrer" onError={() => setBroken(true)} />;
  }
  return <span className="avatar-img avatar-img--letter" style={style} aria-hidden="true">{name.trim().charAt(0).toUpperCase()}</span>;
}
